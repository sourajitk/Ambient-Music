import subprocess
import sys
import os
import requests
import argparse

COMMIT_HISTORY_FILENAME = "commit_history.txt"
CHANGELOG_FILENAME = "changelog.txt"
GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={api_key}"


def run_command(command, can_fail=False):
    """Executes a shell command and returns its output, handling errors."""
    try:
        result = subprocess.run(
            command, check=True, text=True, capture_output=True, shell=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        if can_fail:
            return None
        print(f"A git command failed: '{command}'", file=sys.stderr)
        print(f"Error details: {e.stderr.strip()}", file=sys.stderr)
        sys.exit(1)


def get_commit_history():
    """Get commit history based on tags -output to file"""
    print("Analyzing git tags to determine commit range...")
    # Get a list of all tags, sorted by version number in descending order.
    sorted_tags_str = run_command("git tag --sort=-v:refname")
    all_tags = sorted_tags_str.split("\n") if sorted_tags_str else []
    range_spec = ""

    if len(all_tags) >= 2:
        # The most recent tag is the first in our sorted list
        latest_tag = all_tags[0]
        # The previous tag is the second
        previous_tag = all_tags[1]
        range_spec = f"{previous_tag}..{latest_tag}"
        print(
            f"Lotta releases, getting commits between {previous_tag} and {latest_tag}."
        )

    # For resuablity, if this is the first push, take the only tag into account
    elif len(all_tags) == 1:
        single_tag = all_tags[0]
        range_spec = single_tag
        print(f"Found only one tag. Getting all commits up to '{single_tag}'.")

    else:
        # No tags exist, so get every commit in the repository
        range_spec = "HEAD"
        print("No tags found. Getting everything since root commit")

    commit_history = run_command(f'git log {range_spec} --pretty=format:"- %s"')
    if not commit_history:
        commit_history = "No new commits found in the specified range."

    try:
        with open(COMMIT_HISTORY_FILENAME, "w") as f:
            f.write(commit_history + "\n")
        print(f"Successfully saved raw commit list to '{COMMIT_HISTORY_FILENAME}'")
        return commit_history
    except IOError as e:
        print(
            f"Error writing to file '{COMMIT_HISTORY_FILENAME}': {e}", file=sys.stderr
        )
        sys.exit(1)


def generate_changelog_from_history(api_key, history):
    """Sends commit history to Gemini and returns the generated changelog."""
    print("\nSending commit history to the Gemini API...")
    if not api_key:
        print("Error: Gemini API key is missing.", file=sys.stderr)
        print(
            "Please provide it using the --api-key flag or set the GEMINI_API_KEY env var.",
            file=sys.stderr,
        )
        sys.exit(1)

    # This is wild cuz this is AI training AI lmaoo
    prompt = (
        "You are an expert release manager. Your task is to write a user-friendly changelog. "
        "Analyze the following list of git commit messages and summarize them into a clean, "
        "human-readable changelog for a new software release. "
        "Group related changes under clear headings like 'New Features', 'Bug Fixes', and 'Improvements'.\n\n"
        "Here are the commit messages:\n"
        f"```\n{history}\n```"
    )

    headers = {"Content-Type": "application/json"}
    payload = {"contents": [{"parts": [{"text": prompt}]}]}

    # Time for some RESTfulness
    try:
        response = requests.post(
            GEMINI_API_URL.format(api_key=api_key),
            headers=headers,
            json=payload,
            timeout=60,
        )
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        #print(f"Error calling Gemini API: {e}", file=sys.stderr)
        sys.exit(1)

    try:
        response_json = response.json()
        changelog = response_json["candidates"][0]["content"]["parts"][0]["text"]
        return changelog
    except (KeyError, IndexError, TypeError) as e:
        #print(f"Error parsing Gemini API response: {e}", file=sys.stderr)
        #print(f"Full API Response: {response.text}", file=sys.stderr)
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--api-key",
        default=os.environ.get("GEMINI_API_KEY"),
    )
    args = parser.parse_args()

    commit_history = get_commit_history()
    changelog_text = generate_changelog_from_history(args.api_key, commit_history)

    try:
        with open(CHANGELOG_FILENAME, "w") as f:
            f.write(changelog_text + "\n")
        print(f"\nLog saved: '{CHANGELOG_FILENAME}'")
    except IOError as e:
        print(
            f"Error writing changelog '{CHANGELOG_FILENAME}': {e}",
            file=sys.stderr,
        )


if __name__ == "__main__":
    main()
