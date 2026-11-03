import csv
import json
import math
import sys
from pathlib import Path


def load_measurements(file_path):
    measurements = []
    with open(file_path, encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        columns = ["serial_number", "frequency_hz", "acceleration_m_s2"]
        if reader.fieldnames != columns:
            raise ValueError("В CSV должны быть столбцы: " + ", ".join(columns))

        for row_number, row in enumerate(reader, start=2):
            serial = (row["serial_number"] or "").strip()
            if not serial:
                raise ValueError(f"В строке {row_number} нет номера изделия")
            try:
                frequency = float(row["frequency_hz"])
                acceleration = float(row["acceleration_m_s2"])
            except (ValueError, TypeError):
                raise ValueError(f"В строке {row_number} неверно записаны числа")

            if not math.isfinite(frequency) or frequency <= 0:
                raise ValueError(f"В строке {row_number} неверная частота")
            if not math.isfinite(acceleration) or acceleration < 0:
                raise ValueError(f"В строке {row_number} неверное ускорение")
            measurements.append({
                "serial_number": serial,
                "frequency_hz": frequency,
                "acceleration_m_s2": acceleration,
            })

    if not measurements:
        raise ValueError("В файле нет измерений")
    return measurements


def load_limits(file_path):
    with open(file_path, encoding="utf-8") as file:
        limits = json.load(file)
    if not isinstance(limits, dict):
        raise ValueError("Настройки должны быть объектом JSON")
    for name in ["target_hz", "tolerance"]:
        value = limits.get(name)
        if type(value) not in [int, float] or not math.isfinite(value):
            raise ValueError(f"Неверное значение настройки {name}")
    if limits["target_hz"] <= 0 or limits["tolerance"] < 0:
        raise ValueError("Частота должна быть положительной, а допуск неотрицательным")
    if limits["target_hz"] - limits["tolerance"] <= 0:
        raise ValueError("Нижняя граница частоты должна быть больше нуля")
    return limits


def calculate_resonance(frequencies, accelerations):
    # Пока выбираем максимум на сетке частот, без уточнения между точками.
    if len(frequencies) < 3 or len(frequencies) != len(accelerations):
        raise ValueError("Нужно не менее трех пар частоты и ускорения")
    if len(set(frequencies)) != len(frequencies):
        raise ValueError("В таблице есть повторяющиеся частоты")
    maximum = max(accelerations)
    if maximum <= 0 or accelerations.count(maximum) > 1:
        raise ValueError("Не удалось найти один положительный максимум")
    frequency = frequencies[accelerations.index(maximum)]
    if frequency == min(frequencies) or frequency == max(frequencies):
        raise ValueError("Максимум на краю таблицы. Нужно расширить диапазон частот")
    return frequency


def analyze_measurements(measurements, limits):
    serial = measurements[0]["serial_number"]
    for row in measurements:
        if row["serial_number"] != serial:
            raise ValueError("Один файл должен содержать данные одного изделия")

    frequencies = [row["frequency_hz"] for row in measurements]
    accelerations = [row["acceleration_m_s2"] for row in measurements]
    frequency = calculate_resonance(frequencies, accelerations)
    lower = limits["target_hz"] - limits["tolerance"]
    upper = limits["target_hz"] + limits["tolerance"]
    if lower <= frequency <= upper:
        status = "Годен"
    else:
        status = "Не годен"
    return {
        "serial_number": serial,
        "peak_frequency_hz": frequency,
        "min_frequency_hz": lower,
        "max_frequency_hz": upper,
        "status": status,
    }


def save_result(result, file_path):
    with open(file_path, "w", encoding="utf-8") as file:
        json.dump(result, file, ensure_ascii=False, indent=4)
        file.write("\n")


def main():
    root = Path(__file__).resolve().parent.parent
    if len(sys.argv) == 1:
        data_path = root / "examples" / "measurements.csv"
        limits_path = root / "configs" / "calibration_limits.json"
        result_path = root / "result.json"
    elif len(sys.argv) == 4:
        data_path, limits_path, result_path = map(Path, sys.argv[1:])
    else:
        print("Запуск: python3 src/analyzer.py [данные.csv настройки.json результат.json]")
        return 1

    try:
        if result_path.resolve() in [data_path.resolve(), limits_path.resolve()]:
            raise ValueError("Результат нельзя записывать поверх входных данных")
        measurements = load_measurements(data_path)
        limits = load_limits(limits_path)
        result = analyze_measurements(measurements, limits)
        save_result(result, result_path)
    except (OSError, ValueError, csv.Error) as error:
        print(f"Ошибка: {error}")
        return 1

    print(f"Изделие: {result['serial_number']}")
    print(f"Частота максимального отклика: {result['peak_frequency_hz']} Гц")
    print(f"Результат проверки частоты: {result['status']}")
    print(f"Результат сохранен в {result_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
