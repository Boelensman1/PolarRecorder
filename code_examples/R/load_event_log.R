library(jsonlite)
library(dplyr)

load_event_log <- function(folder) {
  files <- list.files(folder, pattern = "^EVENT_LOG", full.names = TRUE)
  lines <- unlist(lapply(files, readLines, warn = FALSE))
  lapply(lines[nzchar(lines)], fromJSON)
}

timezone <- "Europe/Amsterdam"
entries <- load_event_log("data/recording")

# Flatten 'data' arrays into one dataframe, tagged with phoneTimestamp
events <- bind_rows(lapply(entries, function(e) {
  df <- as.data.frame(e$data)
  df$phoneTimestamp <- e$phoneTimestamp
  df
}))

# The same event 'index' reappears whenever an event is updated (e.g. relabeled).
# Keep only the latest version per index.
events <- events %>%
  arrange(desc(phoneTimestamp)) %>%
  distinct(index, .keep_all = TRUE) %>%
  arrange(index)

events$event_time <- as.POSIXct(events$timestamp / 1000, origin = "1970-01-01", tz = timezone)
events$seconds_from_start <- (events$timestamp - events$recordingStartTime) / 1000

print(events[, c("index", "label", "event_time", "seconds_from_start")])
