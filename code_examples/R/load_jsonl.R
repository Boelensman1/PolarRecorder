library(jsonlite)
library(dplyr)

load_jsonl <- function(folder, dataType, timezone) {
  # Validate inputs
  if (!dir.exists(folder)) stop("Provided folder does not exist.")
  if (!is.character(dataType) || length(dataType) != 1) stop("dataType must be a single character vector string.")
  if (!is.character(timezone) || length(timezone) != 1) stop("timezone must be a single character vector string.")
  
  # Get all filenames that start with the dataType prefix (e.g., "ECG", "ECG_part2.jsonl.json", etc.)
  files <- list.files(path = folder, pattern = paste0("^", dataType), full.names = TRUE)
  if (length(files) == 0) stop(paste("No files found starting with", dataType, "in", folder))
  
  # Read and parse all JSONL lines
  raw_lines <- unlist(lapply(files, readLines, warn = FALSE), use.names = FALSE)
  entries <- lapply(raw_lines, function(line) fromJSON(line))
  
  # Extract metadata from first entry
  recording_name <- entries[[1]]$recordingName
  first_data_received_at <- as.POSIXct((entries[[1]]$phoneTimestamp) / 1000,
                                origin = "1970-01-01", tz = timezone)
  
  # Combine 'data' fields into one dataframe
  data <- bind_rows(lapply(entries, function(entry) as.data.frame(entry$data)))
  
  # Return both data and metadata
  return(list(data = data, recordingName = recording_name, firstDataReceivedAt = first_data_received_at))
}

# Usage example, where an ECG recording is saved in data/recording:
result <- load_jsonl("data/recording", "ECG", "Europe/Amsterdam")

# View the first few rows
head(result$data)

# View the metadata
result$recordingName
result$firstDataReceivedAt
