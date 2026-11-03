import math


def check_response(points, window_size):
    """Проверка входных данных перед расчетом. Точки уже упорядочены."""
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
            raise ValueError("Частота должна быть больше нуля, ускорение — не меньше нуля")
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
    """Алгоритм 1. Вход проверяется функцией check_response до вызова."""
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


def summarize_batch(results):
    """Алгоритм 2. Один проход по результатам проверок."""
    passed = failed = review = 0
    for result in results:
        if result["status"] == "PASS":
            passed += 1
        elif result["status"] == "FAIL":
            failed += 1
        else:
            review += 1
    total = passed + failed + review
    if total == 0:
        pass_percent = None
    else:
        pass_percent = 100 * passed / total
    return {"total": total, "passed": passed, "failed": failed,
            "review": review, "pass_percent": pass_percent}
