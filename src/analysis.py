import math
from datetime import datetime


def check_response(points, window_size):
    if type(window_size) is not int or window_size < 1 or window_size % 2 == 0:
        raise ValueError("Размер окна должен быть положительным нечетным числом")
    if len(points) < window_size + 2:
        raise ValueError("После сглаживания должны остаться хотя бы три точки")
    for point in points:
        for field in ("frequency_hz", "acceleration_m_s2"):
            value = point[field]
            if type(value) not in (int, float) or not math.isfinite(value):
                raise ValueError("Частота и ускорение должны быть конечными числами")
        if point["frequency_hz"] <= 0 or point["acceleration_m_s2"] < 0:
            raise ValueError("Частота должна быть больше нуля, ускорение не меньше нуля")
        if not math.isfinite(point["acceleration_m_s2"] * window_size):
            raise ValueError("Ускорение слишком велико для расчета суммы окна")
    step = points[1]["frequency_hz"] - points[0]["frequency_hz"]
    if step <= 0:
        raise ValueError("Частоты должны возрастать")
    for i in range(1, len(points)):
        if not math.isclose(
            points[i]["frequency_hz"] - points[i - 1]["frequency_hz"],
            step, rel_tol=1e-9, abs_tol=1e-9,
        ):
            raise ValueError("Нужна равномерная возрастающая сетка частот без повторов")


def analyze_response(points, window_size, lower_limit, upper_limit):
    smoothed = []
    half = window_size // 2
    for start in range(len(points) - window_size + 1):
        total = 0.0
        for offset in range(window_size):
            total += points[start + offset]["acceleration_m_s2"]
        smoothed.append({
            "frequency_hz": points[start + half]["frequency_hz"],
            "acceleration_m_s2": total / window_size,
        })

    maximum = max(point["acceleration_m_s2"] for point in smoothed)
    peaks = []
    for i, point in enumerate(smoothed):
        if math.isclose(point["acceleration_m_s2"], maximum, rel_tol=1e-9, abs_tol=1e-12):
            peaks.append(i)
    if maximum <= 0 or len(peaks) != 1:
        return {"status": "REVIEW", "peak_frequency_hz": None,
                "reason": "Нет одного положительного максимума"}
    peak = peaks[0]
    if peak == 0 or peak == len(smoothed) - 1:
        return {"status": "REVIEW", "peak_frequency_hz": None,
                "reason": "Максимум на краю сглаженной характеристики: расширьте диапазон"}
    frequency = smoothed[peak]["frequency_hz"]
    if lower_limit <= frequency <= upper_limit:
        status = "PASS"
    else:
        status = "FAIL"
    return {"status": status, "peak_frequency_hz": frequency,
            "reason": "Проверен допуск частоты максимального отклика"}


def check_started_at(value):
    if type(value) is not str:
        raise ValueError("Время проверки должно быть строкой ISO 8601")
    try:
        timestamp = datetime.fromisoformat(value)
    except ValueError as error:
        raise ValueError("Неверное время начала проверки") from error
    if timestamp.utcoffset() is None:
        raise ValueError("Во времени проверки нужен часовой пояс")
    return timestamp


def summarize_batch(results):
    ordered = sorted(results, key=lambda row: (
        row["product_id"], check_started_at(row["started_at"]), row["run_id"],
    ))
    all_counts = {"PASS": 0, "FAIL": 0, "REVIEW": 0}
    final_counts = {"PASS": 0, "FAIL": 0, "REVIEW": 0}
    unique_products = 0
    for i, result in enumerate(ordered):
        status = result["status"]
        all_counts[status] += 1
        last_for_product = (i + 1 == len(ordered) or
                            ordered[i + 1]["product_id"] != result["product_id"])
        if last_for_product:
            unique_products += 1
            final_counts[status] += 1
    total = len(ordered)
    if total == 0:
        pass_percent = final_pass_percent = None
    else:
        pass_percent = 100 * all_counts["PASS"] / total
        final_pass_percent = 100 * final_counts["PASS"] / unique_products
    return {"total": total, "passed": all_counts["PASS"], "failed": all_counts["FAIL"],
            "review": all_counts["REVIEW"], "pass_percent": pass_percent,
            "unique_products": unique_products,
            "repeated_checks": total - unique_products,
            "final_passed": final_counts["PASS"], "final_failed": final_counts["FAIL"],
            "final_review": final_counts["REVIEW"], "final_pass_percent": final_pass_percent}
