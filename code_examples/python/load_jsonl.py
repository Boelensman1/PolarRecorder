import os
import json
import pandas as pd
from datetime import datetime
import pytz

def load_jsonl(folder, data_type, tz):
    # Validate inputs
    if not os.path.isdir(folder):
        raise ValueError("Provided folder does not exist.")

    # Get all filenames that start with the dataType prefix (e.g., "ECG", "ECG_part2.jsonl.json", etc.)
    files = [os.path.join(folder, f) for f in os.listdir(folder) if f.startswith(data_type)]
    if not files:
        raise FileNotFoundError(f"No files found starting with '{data_type}' in '{folder}'")

    # Read and parse all JSONL lines
    entries = []
    for file in files:
        with open(file, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f, start=1):
                try:
                    entries.append(json.loads(line.strip()))
                except json.JSONDecodeError as e:
                    raise ValueError(f"Invalid JSON on line {i} of '{file}': {e}")

    # Extract metadata from first entry
    first_entry = entries[0]
    recording_name = first_entry.get("recordingName")
    timestamp_ms = first_entry["phoneTimestamp"]
    first_data_received_at = datetime.fromtimestamp(timestamp_ms / 1000, pytz.timezone(tz))

    # Combine 'data' fields into one dataframe
    data_frames = [pd.DataFrame(entry["data"]) for entry in entries]
    data = pd.concat(data_frames, ignore_index=True)

    # Return both data and metadata
    return {
        "data": data,
        "recordingName": recording_name,
        "firstDataReceivedAt": first_data_received_at
    }


# Usage example, where an ECG recording is saved in data/recording:
result = load_jsonl("data/recording", "ECG", "Europe/Amsterdam")

# View first few rows
print(result['data'].head())

# View the metadata
print(result['recordingName'])
print(result['firstDataReceivedAt'])
