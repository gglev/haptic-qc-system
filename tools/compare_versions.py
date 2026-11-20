"""Сравнение результатов Python и Java на одинаковых входных данных."""
import argparse
import copy
import json
import os
from pathlib import Path
import random
import subprocess
import sys
import tempfile


def run_comparison(python_file, java_directory):
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else 'java'
    classpath = os.pathsep.join(str(java_directory / p) for p in
                               ('build/classes', 'lib/gson-2.15.0.jar'))
    if not (java_directory / 'build/classes/qc/app/Main.class').is_file():
        raise ValueError('Сначала выполните сборку Java по README')
    base = json.loads((java_directory / 'examples/batch.json').read_text(encoding='utf-8'))
    repeated = json.loads((java_directory / 'examples/repeated_batch.json').read_text(encoding='utf-8'))
    cases = [('исходный пример', base), ('повторная проверка', repeated)]
    for window in (1, 3, 5):
        data = copy.deepcopy(base)
        data['recipes'][0]['window_size'] = window
        cases.append((f'окно {window}', data))
    for name, values in [('нули', [0] * 7), ('плато', [2] * 7),
                         ('левый край', [9, 8, 7, 6, 5, 4, 3]),
                         ('правый край', [3, 4, 5, 6, 7, 8, 9]),
                         ('малые числа', [0, 0, 1e-13, 0, 0, 0, 0]),
                         ('большие числа', [v * 1e305 for v in [1, 3, 8, 3, 1, .5, .2]])]:
        data = copy.deepcopy(base)
        for point, value in zip(data['measurements'][:7], values):
            point['acceleration_m_s2'] = value
        cases.append((name, data))
    for lower, upper in [(170, 180), (160, 170), (175, 200), (150, 165)]:
        data = copy.deepcopy(base)
        data['recipe_limits'][0].update(lower_limit=lower, upper_limit=upper)
        cases.append((f'границы {lower} {upper}', data))
    data = copy.deepcopy(base)
    data.update(test_runs=[], measurements=[])
    cases.append(('пустая партия', data))
    for times in [('2026-11-19T08:00:00Z', '2026-11-19T10:00:00+03:00'),
                  ('2026-11-19T07:00:00Z', '2026-11-19T10:00:00+03:00'),
                  ('2026-11-19T07:00:00.1234569Z', '2026-11-19T07:00:00.1234561Z')]:
        data = copy.deepcopy(repeated)
        data['test_runs'][1]['started_at'], data['test_runs'][3]['started_at'] = times
        cases.append(('порядок времени ' + str(times), data))
    randomizer = random.Random(20261120)
    for i in range(60):
        data = copy.deepcopy(repeated if i % 2 else base)
        data['recipes'][0]['window_size'] = randomizer.choice([1, 3, 5])
        for point in data['measurements']:
            point['acceleration_m_s2'] = randomizer.randrange(12) / randomizer.choice([1, 10, 100])
        randomizer.shuffle(data['test_runs'])
        randomizer.shuffle(data['measurements'])
        cases.append((f'набор {i + 1}', data))
    invalid = [
        ('recipes', 'window_size', 0), ('recipes', 'window_size', 2),
        ('recipes', 'window_size', 1.0), ('recipes', 'window_size', 99),
        ('test_runs', 'started_at', '2026-11-19T10:00:00'),
        ('test_runs', 'status', 'PASS'), ('test_runs', 'operator_id', 'missing'),
        ('measurements', 'acceleration_m_s2', -1),
        ('measurements', 'acceleration_m_s2', True),
        ('measurements', 'frequency_hz', '150'),
        ('measurements', 'frequency_hz', 151), ('measurements', 'frequency_hz', 0),
        ('measurements', 'point_no', 1.0), ('measurements', 'point_no', 0),
        ('measurements', 'run_id', 'missing'),
        ('recipe_limits', 'lower_limit', 200),
        ('parameters', 'unit', 'м/с'), ('products', 'lot_id', 'missing'),
    ]
    for table, field, value in invalid:
        data = copy.deepcopy(base)
        data[table][0][field] = value
        cases.append((f'ошибка {table}.{field}={value}', data))
    for table in ['products', 'measurements', 'recipe_limits']:
        data = copy.deepcopy(base)
        data[table].append(copy.deepcopy(data[table][0]))
        cases.append(('повтор ключа ' + table, data))
    with tempfile.TemporaryDirectory(prefix='qc-compare-') as temporary:
        temporary = Path(temporary)
        source = temporary / 'input.json'
        outputs = [temporary / 'python.json', temporary / 'java.json']
        for name, data in cases:
            source.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
            for output in outputs:
                output.write_text('old result', encoding='utf-8')
            commands = [[sys.executable, str(python_file), str(source), str(outputs[0])],
                        [java, '-cp', classpath, 'qc.app.Main', str(source), str(outputs[1])]]
            processes = [subprocess.run(command, capture_output=True, text=True) for command in commands]
            codes = [p.returncode for p in processes]
            if codes[0] != codes[1]:
                raise AssertionError(f'{name}: разные коды завершения {codes}\n' + '\n'.join(p.stdout + p.stderr for p in processes))
            if codes[0] == 0:
                actual = [json.loads(p.read_text(encoding='utf-8')) for p in outputs]
                if actual[0] != actual[1]:
                    raise AssertionError(f'{name}: разные JSON\nPython: {actual[0]}\nJava: {actual[1]}')
            elif any(p.read_text(encoding='utf-8') != 'old result' for p in outputs):
                raise AssertionError(f'{name}: поврежден старый результат')
    print(f'Python и Java совпали: {len(cases)} наборов данных')
    print('Проверены JSON, коды завершения и сохранность старого протокола при ошибке')


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--python-file', type=Path, default=root / 'src/main.py')
    parser.add_argument('--java-dir', type=Path, default=root / 'src/java')
    arguments = parser.parse_args()
    try:
        run_comparison(arguments.python_file.resolve(), arguments.java_dir.resolve())
    except (OSError, ValueError, AssertionError) as error:
        print(error)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
