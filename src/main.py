import json
import math
import os
import sys
import tempfile
from pathlib import Path

from analysis import analyze_response, check_response, summarize_batch


def read_number(value, name):
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError(f"{name}: нужно конечное число")
    return value


def index_records(data, table, key):
    rows = data.get(table)
    if not isinstance(rows, list):
        raise ValueError(f"{table}: нужен список записей")
    index = {}
    for row in rows:
        if not isinstance(row, dict) or type(row.get(key)) is not str or not row[key].strip():
            raise ValueError(f"{table}: неверный ключ {key}")
        if row[key] in index:
            raise ValueError(f"{table}: повтор ключа {row[key]}")
        index[row[key]] = row
    return index


def load_batch(path):
    with open(path, encoding="utf-8") as file:
        data = json.load(file)
    if not isinstance(data, dict):
        raise ValueError("Входной файл должен содержать объект JSON")
    tables = {}
    for table, key in (
        ("product_models", "model_id"), ("lots", "lot_id"),
        ("products", "product_id"), ("employees", "employee_id"),
        ("recipes", "recipe_id"), ("parameters", "parameter_id"),
        ("test_runs", "run_id"),
    ):
        tables[table] = index_records(data, table, key)
    for table, field, parent in (
        ("lots", "model_id", "product_models"), ("products", "lot_id", "lots"),
        ("recipes", "model_id", "product_models"), ("recipes", "approved_by", "employees"),
        ("test_runs", "product_id", "products"), ("test_runs", "recipe_id", "recipes"),
        ("test_runs", "operator_id", "employees"),
    ):
        for row in tables[table].values():
            if row.get(field) not in tables[parent]:
                raise ValueError(f"{table}: не найдена связанная запись {field}")
    for table, fields in (
        ("product_models", ("name",)), ("lots", ("lot_number",)),
        ("products", ("serial_number",)), ("employees", ("login", "full_name")),
        ("recipes", ("version", "approved_at")),
        ("parameters", ("code", "name", "unit")),
        ("test_runs", ("started_at", "status")),
    ):
        for row in tables[table].values():
            for field in fields:
                if type(row.get(field)) is not str or not row[field].strip():
                    raise ValueError(f"{table}: нужно заполнить {field}")
    for table, fields in (
        ("product_models", ("name",)), ("lots", ("lot_number",)),
        ("products", ("serial_number",)), ("employees", ("login",)),
        ("parameters", ("code",)), ("recipes", ("model_id", "version")),
    ):
        seen = set()
        for row in tables[table].values():
            values = tuple(row[field] for field in fields)
            if values in seen:
                raise ValueError(f"{table}: повтор значений {fields}")
            seen.add(values)

    limits = {}
    if not isinstance(data.get("recipe_limits"), list):
        raise ValueError("recipe_limits: нужен список")
    for row in data["recipe_limits"]:
        key = (row["recipe_id"], row["parameter_id"])
        if key in limits or key[0] not in tables["recipes"] or key[1] not in tables["parameters"]:
            raise ValueError("Повтор допуска или неверная ссылка на методику/параметр")
        lower = read_number(row["lower_limit"], "lower_limit")
        upper = read_number(row["upper_limit"], "upper_limit")
        if lower <= 0 or lower > upper:
            raise ValueError("Неверные границы допуска частоты")
        limits[key] = row

    points = {run_id: [] for run_id in tables["test_runs"]}
    if not isinstance(data.get("measurements"), list):
        raise ValueError("measurements: нужен список")
    keys = set()
    for row in data["measurements"]:
        run_id = row["run_id"]
        point_no = row["point_no"]
        if run_id not in points or type(point_no) is not int or point_no < 1:
            raise ValueError("Неверная ссылка измерения или номер точки")
        if (run_id, point_no) in keys:
            raise ValueError("Повтор номера точки в проверке")
        keys.add((run_id, point_no))
        points[run_id].append(row)
    prepared = []
    for run in tables["test_runs"].values():
        if run["status"] != "NEW":
            raise ValueError("На вход принимаются только новые проверки со статусом NEW")
        product = tables["products"][run["product_id"]]
        lot = tables["lots"][product["lot_id"]]
        recipe = tables["recipes"][run["recipe_id"]]
        if lot["model_id"] != recipe["model_id"]:
            raise ValueError("Методика не соответствует модели изделия")
        parameter_ids = [p["parameter_id"] for p in tables["parameters"].values()
                         if p["code"] == "peak_frequency_hz" and p["unit"] == "Гц"]
        if len(parameter_ids) != 1:
            raise ValueError("Нужен один параметр peak_frequency_hz с единицей Гц")
        limit = limits.get((recipe["recipe_id"], parameter_ids[0]))
        if limit is None:
            raise ValueError("Для методики не задан допуск частоты")
        ordered = sorted(points[run["run_id"]], key=lambda row: row["point_no"])
        check_response(ordered, recipe["window_size"])
        prepared.append({"run": run, "product": product, "recipe": recipe,
                         "limit": limit, "points": ordered, "lot_id": lot["lot_id"]})
    if len({row["lot_id"] for row in prepared}) > 1:
        raise ValueError("Один запуск обрабатывает проверки одной партии")
    return prepared


def process_batch(prepared):
    results = []
    for item in prepared:
        limit = item["limit"]
        result = analyze_response(item["points"], item["recipe"]["window_size"],
                                  limit["lower_limit"], limit["upper_limit"])
        result.update({"run_id": item["run"]["run_id"],
                       "product_id": item["product"]["product_id"],
                       "serial_number": item["product"]["serial_number"],
                       "recipe_id": item["recipe"]["recipe_id"],
                       "lower_limit": limit["lower_limit"],
                       "upper_limit": limit["upper_limit"]})
        results.append(result)
    return {"test_runs": results, "summary": summarize_batch(results)}


def save_protocol(result, path):
    """Старый протокол заменяется только после полной успешной записи."""
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         prefix=".protocol-", delete=False) as file:
            temporary_path = Path(file.name)
            json.dump(result, file, ensure_ascii=False, indent=2, allow_nan=False)
            file.write("\n")
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None and temporary_path.exists():
            temporary_path.unlink()


def main():
    root = Path(__file__).resolve().parent
    if len(sys.argv) == 1:
        source = root / "examples" / "batch.json"
        target = Path("result.json")
    elif len(sys.argv) == 3:
        source, target = map(Path, sys.argv[1:])
    else:
        print("Запуск: python3 src/main.py [партия.json результат.json]")
        return 1
    try:
        if source.resolve() == target.resolve():
            raise ValueError("Нельзя записывать результат поверх входного файла")
        result = process_batch(load_batch(source))
        save_protocol(result, target)
    except (OSError, ValueError, KeyError, TypeError, OverflowError) as error:
        print(f"Ошибка обработки: {error}")
        return 1
    for run in result["test_runs"]:
        print(f"{run['serial_number']}: {run['status']}; частота: {run['peak_frequency_hz']}")
    summary = result["summary"]
    print(f"Всего: {summary['total']}; годных: {summary['passed']}; "
          f"не годных: {summary['failed']}; на повторную проверку: {summary['review']}")
    print(f"Протокол сохранен: {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
