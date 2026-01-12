import subprocess
import sys
import os
import requests
import argparse

COMMIT_HISTORY_FILENAME = "commit_history.txt"
CHANGELOG_FILENAME = "changelog.txt"
GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"


def run_command(command, can_fail=False):
    """Executes a shell command and returns its output, handling errors."""
    print(f"\n[run_command] Running command: {command}")
    try:
        result = subprocess.run(
            command, check=True, text=True, capture_output=True, shell=True
        )
        print(f"[run_command] Command succeeded. Output:\n{result.stdout.strip()}")
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"[run_command] Command failed: {command}", file=sys.stderr)
        print(f"[run_command] Return code: {e.returncode}", file=sys.stderr)
        print(f"[run_command] Stdout: {e.stdout.strip()}", file=sys.stderr)
        print(f"[run_command] Stderr: {e.stderr.strip()}", file=sys.stderr)
        if can_fail:
            print("[run_command] Failure is allowed, returning None.")
            return None
        sys.exit(1)


def get_commit_history():
    """Get commit history based on tags -output to file"""
    print("\n[get_commit_history] Starting commit history extraction...")

    print("[get_commit_history] Getting sorted list of git tags...")
    sorted_tags_str = run_command("git tag --sort=-v:refname")
    all_tags = sorted_tags_str.split("\n") if sorted_tags_str else []
    print(f"[get_commit_history] All tags found: {all_tags}")

    range_spec = ""

    if len(all_tags) >= 2:
        latest_tag = all_tags[0]
        previous_tag = all_tags[1]
        range_spec = f"{previous_tag}..{latest_tag}"
        print(f"[get_commit_history] Multiple tags found. Using range: {range_spec}")
    elif len(all_tags) == 1:
        single_tag = all_tags[0]
        range_spec = single_tag
        print(f"[get_commit_history] Only one tag found: {single_tag}")
    else:
        range_spec = "HEAD"
        print("[get_commit_history] No tags found. Using full commit history from HEAD.")

    log_command = f'git log {range_spec} --pretty=format:"- %s"'
    print(f"[get_commit_history] Fetching commit messages with: {log_command}")
    commit_history = run_command(log_command)

    if not commit_history:
        print("[get_commit_history] No commits found in the range.")
        commit_history = "No new commits found in the specified range."

    print(f"[get_commit_history] Commit history:\n{commit_history}")

    print(f"[get_commit_history] Writing commit history to '{COMMIT_HISTORY_FILENAME}'...")
    try:
        with open(COMMIT_HISTORY_FILENAME, "w") as f:
            f.write(commit_history + "\n")
        print(f"[get_commit_history] Successfully wrote commit history to {COMMIT_HISTORY_FILENAME}")
        return commit_history
    except IOError as e:
        print(f"[get_commit_history] IOError while writing file: {e}", file=sys.stderr)
        sys.exit(1)


def generate_changelog_from_history(api_key, history):
    """Sends commit history to Gemini and returns the generated changelog."""
    print("\n[generate_changelog_from_history] Starting changelog generation via Gemini API...")

    if not api_key:
        print("[generate_changelog_from_history] Error: API key not provided", file=sys.stderr)
        sys.exit(1)

    print("[generate_changelog_from_history] Constructing prompt for Gemini...")
    prompt = (
        "You are an expert release manager. Your task is to write a user-friendly changelog. "
        "Analyze the following list of git commit messages and summarize them into a clean, "
        "human-readable changelog for a new software release. "
        "Group related changes under clear headings like 'New Features', 'Bug Fixes', and 'Improvements'.\n\n"
        "Here are the commit messages:\n"
        f"```\n{history}\n```"
        "Make sure the changelog of this format in the link given:"
        "https://github.com/sourajitk/Ambient-Music/releases/tag/v3.3.2"
        "Don't include any text like: Here's a user-friendly changelog for your new release, crafted by an expert release manager."
        "Have a one liner at the top summarizing what the changes are from the commit messages."
        "Like, This release includes dependency updates and some internal improvements."
        "Do not mention what release number it is like # Release v3.4.0"
        "All headings should just be bolded, not actual, headers"
        "Never use emojis"
    )

    headers = {"Content-Type": "application/json"}
    payload = {"contents": [{"parts": [{"text": prompt}]}]}

    print(f"[generate_changelog_from_history] Sending request to Gemini API with headers: {headers}")
    print(f"[generate_changelog_from_history] Payload:\n{payload}")

    try:
        response = requests.post(
            GEMINI_API_URL.format(api_key=api_key),
            headers=headers,
            json=payload,
            timeout=60,
        )
        print("[generate_changelog_from_history] Response status code:", response.status_code)
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        print(f"[generate_changelog_from_history] Request to Gemini API failed: {e}", file=sys.stderr)
        sys.exit(1)

    print("[generate_changelog_from_history] Parsing response JSON...")
    try:
        response_json = response.json()
        changelog = response_json["candidates"][0]["content"]["parts"][0]["text"]
        print(f"[generate_changelog_from_history] Successfully parsed changelog:\n{changelog}")
        return changelog
    except (KeyError, IndexError, TypeError) as e:
        print("[generate_changelog_from_history] Failed to parse Gemini API response.", file=sys.stderr)
        print(f"[generate_changelog_from_history] Exception: {e}", file=sys.stderr)
        print(f"[generate_changelog_from_history] Raw response:\n{response.text}", file=sys.stderr)
        sys.exit(1)


def main():
    print("\n[main] Starting changelog automation script...")
    parser = argparse.ArgumentParser(description="Generate changelog from git history using Gemini.")
    parser.add_argument(
        "--api-key",
        default=os.environ.get("GEMINI_API_KEY"),
        help="Your Gemini API key. Can also be set via the GEMINI_API_KEY environment variable."
    )
    args = parser.parse_args()

    if args.api_key:
        print("[main] API key provided.")
    else:
        print("[main] Warning: No API key provided.")

    print("[main] Fetching commit history...")
    commit_history = get_commit_history()

    print("[main] Generating changelog from commit history...")
    changelog_text = generate_changelog_from_history(args.api_key, commit_history)

    print(f"[main] Writing changelog to '{CHANGELOG_FILENAME}'...")
    try:
        with open(CHANGELOG_FILENAME, "w") as f:
            f.write(changelog_text + "\n")
        print(f"[main] Successfully wrote changelog to '{CHANGELOG_FILENAME}'")
    except IOError as e:
        print(f"[main] IOError while writing changelog: {e}", file=sys.stderr)
        sys.exit(1)

    print("[main] Script completed successfully.")


if __name__ == "__main__":
    main()
