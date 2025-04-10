#! /usr/local/bin/Rscript

# Load the libraries
library(raster)
library(data.table)
library(sf)
library(sp)
library(dplyr)
library(terra)
library(lubridate)
library(pins)


# Import the raster file and region of interest shapefile.  
# raster file
prec_makueni <- raster::brick("data/gpmStack_makueni_2022-09-01_2022-09-30.tif")

# get the sequence of days covered.
# we cannot name columns using integers hence the need to attach the 'd' character. 
days.seq <- paste0("d", seq(as.Date("2022-9-1"), as.Date("2022-9-30"), by = "days"))

names(prec_makueni) <- days.seq
# region of interest :  Makueni County  
makueni <- st_read("data/shp/makueni_county_bnd/Makueni_county.shp") %>%
    st_transform(crs = 4326) 

# Data extraction   

# rename the layer names of the raster file
names(prec_makueni) <- days.seq

# crop the raster to the region of interest
makueni_raster <- mask(prec_makueni,makueni)

## extract daily precipitation values----
dt_points <- data.table(raster::rasterToPoints(makueni_raster))

## Data munging 
#* melt the data to long format
#* drop the 'd' from the date column values
#* extract year and month from the date column 

dt_points_long <- data.table::melt(dt_points,id.vars = c("x","y"),value.name = "precipitation",variable.name = "date")

dt_points_long <- dt_points_long[, date:= as.Date(gsub("d","",date),format="%Y.%m.%d")]

dt_points_long <- dt_points_long[, year:= year(date)][,month:= month(date)][,day:= day(date)]

# Of course you can extend the munging to a format of your liking...

## Data versioning   

# create a board where you'll pin your date
pins.board <- board_folder("./data/pins.board", versioned = TRUE)

# if its the first time you're running this script uncomment the line below 
# pins.board %>% pins::pin_write(dt_points_long,name = "makueni_precipitation", type = "csv", versioned = TRUE)

# import the previous version so that we can append to it the processed data
previous.version <- pins.board %>% pin_read("makueni_precipitation") 
  
# append the processed data to the previous version  
combined.data <- rbind(previous.version,dt_points_long)
  
pins.board %>% pin_write(combined.data,"makueni_precipitation", type = "csv", versioned = TRUE)

