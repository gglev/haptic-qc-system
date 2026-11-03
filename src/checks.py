import copy
import json
import math
import subprocess
import sys
import tempfile
from pathlib import Path

from analysis import analyze_response, check_response, summarize_batch
from main import load_batch, process_batch, save_protocol


def expect_error(function, *args):
    try:
        function(*args)
    except (ValueError, KeyError, TypeError):
        return
    raise AssertionError("Ожидалась ошибка, но ее не было")


def check_algorithms():
    points = [{"frequency_hz": 150 + 10 * i, "acceleration_m_s2": value}
              for i, value in enumerate([1, 3, 8, 3, 1, .5, .2])]
    check_response(points, 3)
    assert analyze_response(points, 3, 160, 180)["peak_frequency_hz"] == 170
    assert analyze_response(points, 3, 170, 170)["status"] == "PASS"
    assert analyze_response(points, 3, 150, 170)["status"] == "PASS"
    assert analyze_response(points, 3, 180, 190)["status"] == "FAIL"
    assert analyze_response(points, 1, 160, 180)["peak_frequency_hz"] == 170
    plateau = [dict(point, acceleration_m_s2=2) for point in points]
    assert analyze_response(plateau, 3, 160, 180)["status"] == "REVIEW"
    zero = [dict(point, acceleration_m_s2=0) for point in points]
    assert analyze_response(zero, 3, 160, 180)["status"] == "REVIEW"
    edge = [dict(point, acceleration_m_s2=i) for i, point in enumerate(points)]
    assert analyze_response(edge, 3, 160, 180)["status"] == "REVIEW"
    expect_error(check_response, points, 2)
    expect_error(check_response, points, True)
    expect_error(check_response, points[:4], 3)
    duplicate = copy.deepcopy(points)
    duplicate[1]["frequency_hz"] = duplicate[0]["frequency_hz"]
    expect_error(check_response, duplicate, 3)
    uneven = copy.deepcopy(points)
    uneven[1]["frequency_hz"] += 1
    expect_error(check_response, uneven, 3)
    invalid = copy.deepcopy(points)
    invalid[2]["acceleration_m_s2"] = float("nan")
    expect_error(check_response, invalid, 3)
    invalid[2]["acceleration_m_s2"] = -1
    expect_error(check_response, invalid, 3)
    invalid[2]["acceleration_m_s2"] = 1e308
    expect_error(check_response, invalid, 3)
    summary = summarize_batch([{"status": "PASS"}, {"status": "FAIL"}, {"status": "REVIEW"}])
    assert (summary["total"], summary["passed"], summary["failed"], summary["review"]) == (3, 1, 1, 1)
    assert math.isclose(summary["pass_percent"], 100 / 3)
    assert summarize_batch([]) == {"total": 0, "passed": 0, "failed": 0,
                                   "review": 0, "pass_percent": None}


def check_files():
    root = Path(__file__).resolve().parent
    source = root / "examples" / "batch.json"
    expected = json.loads((root / "examples" / "expected_result.json").read_text(encoding="utf-8"))
    assert process_batch(load_batch(source)) == expected
    data = json.loads(source.read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory() as directory:
        directory = Path(directory)
        input_path = directory / "input.json"
        output_path = directory / "output.json"
        shuffled = copy.deepcopy(data)
        shuffled["measurements"].reverse()
        input_path.write_text(json.dumps(shuffled), encoding="utf-8")
        assert process_batch(load_batch(input_path)) == expected

        invalid = copy.deepcopy(data)
        invalid["test_runs"][0]["product_id"] = "missing"
        input_path.write_text(json.dumps(invalid), encoding="utf-8")
        expect_error(load_batch, input_path)
        invalid = copy.deepcopy(data)
        invalid["measurements"].append(invalid["measurements"][0])
        input_path.write_text(json.dumps(invalid), encoding="utf-8")
        expect_error(load_batch, input_path)

        save_protocol(expected, output_path)
        before = output_path.read_bytes()
        expect_error(save_protocol, {"invalid": float("nan")}, output_path)
        assert output_path.read_bytes() == before
        assert not list(directory.glob(".protocol-*"))

        result = subprocess.run([sys.executable, str(root / "main.py"), str(input_path), str(output_path)],
                                capture_output=True, text=True)
        assert result.returncode == 1 and "Ошибка обработки" in result.stdout
        assert output_path.read_bytes() == before

        input_path.write_text(json.dumps(data), encoding="utf-8")
        before_input = input_path.read_bytes()
        result = subprocess.run([sys.executable, str(root / "main.py"), str(input_path), str(input_path)],
                                capture_output=True, text=True)
        assert result.returncode == 1 and input_path.read_bytes() == before_input


def main():
    check_algorithms()
    print("Проверены расчеты, границы допуска, плато, край диапазона, сетка и пустая партия")
    check_files()
    print("Проверены пример JSON, порядок точек, связи, дубликаты и защита файлов при ошибках")
    print("Все проверки пройдены")


if __name__ == "__main__":
    main()
