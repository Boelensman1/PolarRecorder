library(jsonlite)
library(dplyr)

load_jsonl <- function(folder, dataType) {
  # Validate inputs
  if (!dir.exists(folder)) stop("Provided folder does not exist.")
  if (!is.character(dataType) || length(dataType) != 1) stop("dataType must be a single character vector string.")
  
  # Get all filenames that start with the dataType prefix (e.g., "ECG.jsonl", "ECG_part2.jsonl", etc.)
  files <- list.files(path = folder, pattern = paste0("^", dataType), full.names = TRUE)
  if (length(files) == 0) stop(paste("No files found starting with", dataType, "in", folder))
  
  # Read and parse all JSONL lines
  raw_lines <- unlist(lapply(files, readLines, warn = FALSE), use.names = FALSE)
  entries <- lapply(raw_lines, function(line) fromJSON(line))
  
  # Return the result
  return(entries)
}


# Load file, where an ECG recording is saved in data/recording:
entries <- load_jsonl("data/recording", "ECG")

# Timezone
timezone <- "Europe/Amsterdam"

# Extract metadata from first entry
first_entry <- entries[[1]]
recording_name <- first_entry$recordingName
timestamp_ms <- first_entry$phoneTimestamp
first_data_received_at <- as.POSIXct(timestamp_ms / 1000, origin = "1970-01-01", tz = timezone)

# Print metadata
print(recording_name)
print(first_data_received_at)

# Combine 'data' fields into one dataframe
data <- bind_rows(lapply(entries, function(entry) as.data.frame(entry$data)))

# Print first few rows
head(data)

# Construct extended data with 'received_at'
extended_data <- bind_rows(lapply(entries, function(entry) {
  df <- as.data.frame(entry$data)
  received_at <- as.POSIXct(entry$phoneTimestamp / 1000, origin = "1970-01-01", tz = timezone)
  df$received_at <- received_at
  df
}))

# Print first few rows of extended data
head(extended_data)
