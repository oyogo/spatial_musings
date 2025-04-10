
// Load the Sentinel-2 Harmonized Image Collection from Google Earth Engine
var s2 = ee.ImageCollection('COPERNICUS/S2_HARMONIZED');

// Define the area of interest (AOI) : load Kisii county
var county = ee.FeatureCollection('projects/sae-deep/assets/kenya_wards'); 

// Filter by the county FeatureCollection to select feature where subcounty == Butere, Kakamega. You could choose any level as is available in your shapefile (FeatureCollection)
var filtered = county.filter(ee.Filter.eq('subcounty', 'Butere Sub County'));

//Extract the geometry of the filtered feature (boundaries of Kisii county)
var geometry = filtered.geometry();


// Visualize the AOI on the map, outlined in red
Map.addLayer(geometry, {color: 'red'}, 'Farm');
Map.centerObject(geometry);  // Center the map on the AOI

// Filter Sentinel-2 images for the year 2017 and restrict to the AOI
var filtered = s2
  .filter(ee.Filter.date('2020-01-01', '2025-01-01'))  // Temporal filter: Jan 1, 2020 - Dec 31, 2024
  .filter(ee.Filter.bounds(geometry));                // Spatial filter: AOI

// Load the Cloud Score+ dataset for assessing cloud cover quality
var csPlus = ee.ImageCollection('GOOGLE/CLOUD_SCORE_PLUS/V1/S2_HARMONIZED');
var csPlusBands = csPlus.first().bandNames();  // Extract band names from the Cloud Score+ collection

// Link Cloud Score+ data to each Sentinel-2 image to help with cloud masking
var filteredS2WithCs = filtered.linkCollection(csPlus, csPlusBands);

// Function to mask out pixels with poor cloud quality scores
function maskLowQA(image) {
  var qaBand = 'cs';              // 'cs' represents the Cloud Score band
  var clearThreshold = 0.5;       // Only keep pixels with cloud score ≥ 0.5 (considered relatively clear)
  var mask = image.select(qaBand).gte(clearThreshold); // Create mask for clear pixels
  return image.updateMask(mask);  // Apply the mask to the image
}

// Apply the cloud mask to all images in the filtered Sentinel-2 collection
var filteredMasked = filteredS2WithCs.map(maskLowQA);

// Function to calculate NDVI (Normalized Difference Vegetation Index)
// NDVI = (NIR - Red) / (NIR + Red) where B8 = NIR and B4 = Red for Sentinel-2
function addNDVI(image) {
  var ndvi = image.normalizedDifference(['B8', 'B4']).rename('ndvi');  // Compute NDVI and rename the band
  return image.addBands(ndvi);  // Add the NDVI band to the image
}

// Apply the NDVI calculation function to the cloud-masked collection
var withNdvi = filteredMasked.map(addNDVI);

// Create a time-series chart to visualize NDVI changes over time
var chart = ui.Chart.image.series({
  imageCollection: withNdvi.select('ndvi'),  // Select the NDVI band from the collection
  region: geometry,                          // Define the region of interest (AOI)
  reducer: ee.Reducer.mean(),                // Calculate the mean NDVI for the AOI
  scale: 1000                                  // Set spatial resolution to 1000 meters
}).setOptions({
  lineWidth: 1,                               // Customize the chart line width
  pointSize: 2,                               // Customize the chart point size
  title: 'NDVI Time Series',                  // Chart title
  interpolateNulls: true,                     // Connect data points even if some dates are missing
  vAxis: {title: 'NDVI'},                     // Label for the vertical axis
  hAxis: {title: '', format: 'YYYY-MMM'}      // Label for the horizontal axis (formatted by year and month)
});

// Display the NDVI time-series chart in the console
print(chart);
