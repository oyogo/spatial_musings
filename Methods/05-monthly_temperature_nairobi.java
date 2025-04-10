var terraclimate = ee.ImageCollection("IDAHO_EPSCOR/TERRACLIMATE");
var geometry = ee.Geometry.Point([77.54849920033682, 12.91215102400037]);
    
// Assignment
// Use TerraClimate dataset to chart a 50 year time series
// of temparature at any location

// Workflow
// Load the TerraClimate collection
// Select the 'tmmx' band
// Scale the band values
// Filter the scaled collection to the desired date range
// Use ui.Chart.image.series() function to create the chart


// Hint1
// The 'tmnx' band has a scaling factor of 0.1 as per
// https://developers.google.com/earth-engine/datasets/catalog/IDAHO_EPSCOR_TERRACLIMATE#bands
// This means that we need to multiply each pixel value by 0.1
// to obtain the actual temparature value

// map() a function and multiply each image

var tmax = terraclimate.select('tmmx');

// Function that applies the scaling factor to the each image 

// Multiplying creates a new image that doesn't have the same properties
// Use copyProperties() function to copy timestamp to new image
var scaleImage = function(image) {
  return image.multiply(0.1)
    .copyProperties(image,['system:time_start']);
};
var tmaxScaled = tmax.map(scaleImage);

// Hint2
// You will need to specify pixel resolution as the scale parameter 
// in the charting function
// Use projection().nominalScale() to find the 
// image resolution in meters
var image = ee.Image(terraclimate.first())
print(image.projection().nominalScale())