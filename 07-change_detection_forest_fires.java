// Draw the boundaries to extract the polygon coordinates - on GEE

// Define the start and end dates of the fire event
// There was a 10-day fire event in Isiolo county in the areas of Garbatulla and Cherab subcounties 
// There was a mention of 2500 hectares of grassland were affected according to social media reports : https://x.com/ray_omollo/status/1883471976225730626
// 
var fireStart = ee.Date('2025-01-15');
var fireEnd = ee.Date('2025-01-25');

// Center the map on the region of interest
Map.centerObject(geometry, 10);

// Load Sentinel-2 image collection
var s2 = ee.ImageCollection("COPERNICUS/S2");

// Apply spatial filter to select images within the specified geometry
var filtered = s2
  .filter(ee.Filter.bounds(geometry))
  .select('B.*'); // Select all spectral bands

// Load the Cloud Score+ collection for cloud masking
var csPlus = ee.ImageCollection('GOOGLE/CLOUD_SCORE_PLUS/V1/S2_HARMONIZED');
var csPlusBands = csPlus.first().bandNames();

// Link Cloud Score+ bands to each Sentinel-2 image for cloud masking
var filteredS2WithCs = filtered.linkCollection(csPlus, csPlusBands);

// Function to mask pixels with low Cloud Score+ QA scores
function maskLowQA(image) {
  var qaBand = 'cs'; // Cloud Score+ band
  var clearThreshold = 0.5; // Threshold for clear pixels
  var mask = image.select(qaBand).gte(clearThreshold);
  return image.updateMask(mask);
}

// Apply cloud masking to the image collection
var filteredMasked = filteredS2WithCs.map(maskLowQA);

// Create pre-fire and post-fire median composites
var before = filteredMasked
  .filter(ee.Filter.date(fireStart.advance(-2, 'month'), fireStart))
  .median();

var after = filteredMasked
  .filter(ee.Filter.date(fireEnd, fireEnd.advance(1, 'month')))
  .median();

// Visualization parameters for False Color (SWIR-based) composite
var swirVis = {
  min: 0.0,
  max: 3000,
  bands: ['B12', 'B8', 'B4'], // SWIR, NIR, Red
};

// Add pre-fire and post-fire images to the map
Map.addLayer(before.clip(geometry), swirVis, 'Before');
Map.addLayer(after.clip(geometry), swirVis, 'After');

// Function to compute the Normalized Burn Ratio (NBR) using NIR (B8) and SWIR-2 (B12)
var addNBR = function(image) {
  var nbr = image.normalizedDifference(['B8', 'B12']).rename(['nbr']);
  return image.addBands(nbr);
};

// Compute NBR before and after the fire
var beforeNbr = addNBR(before).select('nbr');
var afterNbr = addNBR(after).select('nbr');

// Visualization parameters for NBR
var nbrVis = {min: -0.5, max: 0.5, palette: ['white', 'black']};

// Add pre-fire and post-fire NBR to the map
Map.addLayer(beforeNbr.clip(geometry), nbrVis, 'Prefire NBR');
Map.addLayer(afterNbr.clip(geometry), nbrVis, 'Postfire NBR');

// Compute the difference in NBR (dNBR) to identify burned areas
var change = beforeNbr.subtract(afterNbr);

// Define a threshold to classify burned areas
var threshold = 0.2;

// Create a binary mask for burned areas (1 = burned, 0 = unburned)
var burned = change.gt(threshold);

// Add burned area layer to the map
Map.addLayer(burned.clip(geometry), {min: 0, max: 1, palette: ['white', 'red']}, 'Burned', false);

// **Calculate Burned Area in Hectares**
// Multiply the burned area mask by pixel area (in square meters)
var burnedArea = burned.multiply(ee.Image.pixelArea());

// Reduce the burned area over the region of interest to get the total area
var stats = burnedArea.reduceRegion({
  reducer: ee.Reducer.sum(), // Sum all burned pixels
  geometry: geometry,
  scale: 10,  // Sentinel-2 resolution is 10m, but we use 30m to ensure consistency
  maxPixels: 1e13
});

// Convert area from square meters to hectares (1 hectare = 10,000 m²)
var burnedHectares = ee.Number(stats.get('nbr')).divide(10000);

// Print the total burned area in hectares
burnedHectares.evaluate(function(result) {
  print('Total Burned Area (ha):', result);
});

// This method indicates that the total burnt area was about 37787.783 hectares.
// However the mention on social media was 2500
