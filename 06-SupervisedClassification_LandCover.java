// Load Sentinel-2 Surface Reflectance image collection
var s2 = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED');

// Load Kenya ward boundaries from a FeatureCollection
var county = ee.FeatureCollection('projects/sae-deep/assets/kenya_wards'); 

// Filter to select only features where the 'county' attribute is 'Kisii'
var geometry = county.filter(ee.Filter.eq('county', 'Kisii'));

// Center the map on the selected county
Map.centerObject(geometry);

// Filter Sentinel-2 images by:
// - Cloud cover < 30%
// - Date range: 2019-01-01 to 2020-01-01
// - Location: Kisii county
// - Selecting all spectral bands ('B.*' selects all bands starting with 'B')
var filtered = s2
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .filter(ee.Filter.date('2019-01-01', '2020-01-01'))
  .filter(ee.Filter.bounds(geometry))
  .select('B.*');

// Create a median composite (pixel-wise median value across the time series)
var composite = filtered.median();

// Define visualization parameters for true-color RGB (Red, Green, Blue)
var rgbVis = {min: 0.0, max: 3000, bands: ['B4', 'B3', 'B2']};

// Display the median composite, clipped to the Kisii county boundary
Map.addLayer(composite.clip(geometry), rgbVis, 'image');

/* 
   Supervised Classification Process
   ---------------------------------
   1. Collect training data for different land cover classes
   2. Train a classifier using the training data
   3. Classify the image using the trained classifier
   4. Display the classified land cover map
*/

// Define land cover classes:
// - Urban: 0
// - Bare: 1
// - Water: 2
// - Vegetation: 3

// Merge training data from different land cover classes
var gcps = urban.merge(bare).merge(vegetation);

// export the ground collected points to asset for future use
Export.table.toAsset({
  collection: gcps,
  description: 'gcps_kisii'
  });
  
// Sample training data from the composite image using the defined training points
var training = composite.sampleRegions({
  collection: gcps,   // FeatureCollection of labeled training points
  properties: ['landcover'],  // Land cover class label
  scale: 10,          // Resolution of the training samples
  tileScale: 16       // Used for scaling large computations
});

// Train a Random Forest classifier with 50 trees
var classifier = ee.Classifier.smileRandomForest(50).train({
  features: training,  
  classProperty: 'landcover', // Label column
  inputProperties: composite.bandNames() // Features (bands) used for training
});

// Classify the image using the trained model
var classified = composite.classify(classifier);

// Define a color palette for visualization:
// - Urban (0) -> Pink (#cc6d8f)
// - Bare (1) -> Yellow (#ffc107)
// - Vegetation (2) -> Dark Green (#004d40)
var palette = ['#cc6d8f', '#ffc107', '#004d40'];

// Display the classified land cover map, clipped to Kisii county
Map.addLayer(classified.clip(geometry), {min: 0, max: 2, palette: palette}, '2019');
