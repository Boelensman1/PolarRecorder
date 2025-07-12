import os
import json
import pandas as pd
from datetime import datetime
import pytz

def load_jsonl(folder, data_type):
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

    return entries


# Load file, where an ECG recording is saved in data/recording:
result = load_jsonl("data/recording", "ECG")

# Extract metadata from first entry
tz = "Europe/Amsterdam"
first_entry = result[0]
recording_name = first_entry.get("recordingName")
timestamp_ms = first_entry["phoneTimestamp"]
first_data_received_at = datetime.fromtimestamp(timestamp_ms / 1000, pytz.timezone(tz))

# View the metadata
print(recording_name)
print(first_data_received_at)

# Combine 'data' fields into one dataframe
data_frames = [pd.DataFrame(entry['data']) for entry in result]
data = pd.concat(data_frames, ignore_index=True)

# View first few rows
print(data.head())

# Combine 'data' fields into one dataframe with received at info
extended_data_frames = []
for entry in result:
    if "data" in entry:
        df = pd.DataFrame(entry["data"])
        # Add the received timestamp to each row
        received_at = datetime.fromtimestamp(entry["phoneTimestamp"] / 1000, pytz.timezone(tz))
        df["received_at"] = received_at
        extended_data_frames.append(df)

# Concatenate all dataframes
extended_data = pd.concat(extended_data_frames, ignore_index=True)

# View first few rows
print(extended_data.head())
