import glob
import json
import pandas as pd

def load_event_log(folder):
    entries = []
    for f in glob.glob(f"{folder}/EVENT_LOG*.jsonl"):
        with open(f) as fh:
            entries.extend(json.loads(line) for line in fh if line.strip())
    return entries


tz = "Europe/Amsterdam"
entries = load_event_log("data/recording")

# Flatten 'data' arrays into one dataframe, tagged with receive time
events = pd.concat(
    [pd.DataFrame(e["data"]).assign(phoneTimestamp=e["phoneTimestamp"]) for e in entries],
    ignore_index=True,
)

# The same event 'index' reappears whenever an event is updated (e.g. relabeled).
# Keep only the latest version per index.
events = (
    events.sort_values("phoneTimestamp")
          .drop_duplicates("index", keep="last")
          .sort_values("index")
          .reset_index(drop=True)
)

events["event_time"] = pd.to_datetime(events["timestamp"], unit="ms", utc=True).dt.tz_convert(tz)
events["seconds_from_start"] = (events["timestamp"] - events["recordingStartTime"]) / 1000

print(events[["index", "label", "event_time", "seconds_from_start"]])
