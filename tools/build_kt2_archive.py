"""Сборка архива из проверенных файлов. Коммиты и push не выполняются."""
import subprocess
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def git(root, *arguments):
    return subprocess.check_output(["git", "-C", str(root), *arguments], text=True)


def main():
    root = Path(__file__).resolve().parent.parent
    draft = sys.argv[1:] == ["--draft"]
    if sys.argv[1:] not in ([], ["--draft"]):
        print("Запуск: python3 tools/build_kt2_archive.py [--draft]")
        return 1
    if Path(git(root, "rev-parse", "--show-toplevel").strip()).resolve() != root.resolve():
        print("Скрипт должен лежать в tools внутри репозитория haptic-qc-system.")
        return 1
    remote = git(root, "remote", "get-url", "origin").strip().removesuffix(".git")
    if remote not in ("https://github.com/gglev/haptic-qc-system", "git@github.com:gglev/haptic-qc-system"):
        print("У репозитория другой origin. Проверьте папку проекта перед сборкой.")
        return 1
    dates = set(git(root, "log", "3c73c3713df528a753bf55159cb770c5c7be27dd..HEAD",
                    "--date=short", "--format=%ad").splitlines())
    if len(dates) < 3:
        print(f"После КТ1 есть коммиты за {len(dates)} разных дня: {', '.join(sorted(dates))}.")
        print("По памятке нужны минимум три новых коммита в три разных дня.")
        if not draft:
            print("Архив не собран. Для копии на проверку добавьте --draft. Даты Git не изменяются.")
            return 1
    changed = git(root, "status", "--porcelain")
    if changed.strip():
        print("В рабочей папке есть изменения. Архив включит текущее содержимое файлов,")
        print("а git_log — только уже существующие коммиты. Перед сдачей проверьте совпадение с GitHub.")
    subprocess.run([sys.executable, str(root / "src/checks.py")], cwd=root, check=True)

    vcs = root / "vcs"
    vcs.mkdir(exist_ok=True)
    (vcs / "git_log.txt").write_text(
        git(root, "log", "--all", "--date=iso", "--pretty=format:%h %ad %an %s"), encoding="utf-8")
    (vcs / "repo_url.txt").write_text("https://github.com/gglev/haptic-qc-system\n", encoding="utf-8")
    files = ["report.docx", "vcs/git_log.txt", "vcs/repo_url.txt"]
    for name in ("bpmn.bpmn", "bpmn.png", "er_logical.drawio", "er_logical.png",
                 "algo_1.drawio", "algo_1.png", "algo_2.drawio", "algo_2.png"):
        files.append("models/" + name)
    for name in ("README.md", "requirements.txt", "main.py", "analysis.py", "checks.py",
                 "examples/batch.json", "examples/expected_result.json"):
        files.append("src/" + name)
    missing = [name for name in files if not (root / name).is_file()]
    if missing:
        print("Не найдены файлы: " + ", ".join(missing))
        return 1
    destination = root / "dist"
    destination.mkdir(exist_ok=True)
    archive = destination / "ИКБО-31-24_КалининЛА_КТ2.zip"
    with ZipFile(archive, "w", ZIP_DEFLATED) as zip_file:
        for name in files:
            zip_file.write(root / name, name)
    print("Архив: " + str(archive))
    if draft:
        print("Это копия для проверки. Необходимые дни Git и публикация новых файлов еще не подтверждены.")
    return 0


if __name__ == "__main__":
    try:
        exit_code = main()
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Не удалось собрать архив: {error}")
        exit_code = 1
    sys.exit(exit_code)
