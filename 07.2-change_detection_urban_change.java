/*
Export.table.toAsset({
  collection: change,
  description: "urbanchangedetection_newbuildings_syokimauward_change",
  assetId: "projects/sae-deep/assets/change"
});
*/

// Load the administrative boundaries of Kenya at the ward level.
var kenya = ee.FeatureCollection('projects/sae-deep/assets/kenya_wards');

// Filter out Syokimau-Mulolongo Ward from the Kenya wards dataset.
var syokimau = kenya.filter(ee.Filter.eq('ward', 'Syokimau-mulolongo Ward'));

// Load ground-collected points for areas where change (new buildings) has been detected.
var change = ee.FeatureCollection('projects/sae-deep/assets/change'); 

// Load ground-collected points for areas with no detected change.
var nochange = ee.FeatureCollection('projects/sae-deep/assets/nochange'); 

// Load Sentinel-2 satellite imagery from the Copernicus dataset.
var s2 = ee.ImageCollection('COPERNICUS/S2');

// Get the geometry of Syokimau-Mulolongo Ward for spatial filtering.
var geometry = syokimau.geometry();

// Center the map to the region of interest.
Map.centerObject(geometry);

// Define visualization parameters for RGB composite (true color) representation.
var rgbVis = {
  min: 0.0,
  max: 3000,
  bands: ['B4', 'B3', 'B2'],  // Red, Green, and Blue bands for true-color visualization.
};

// **Step 1: Preprocessing Sentinel-2 imagery**

// Filter the Sentinel-2 image collection to the area of interest (Syokimau-Mulolongo Ward).
var filtered = s2.filter(ee.Filter.bounds(geometry));

// Load the Cloud Score Plus (CS+) dataset, which provides cloud probability information.
var csPlus = ee.ImageCollection('GOOGLE/CLOUD_SCORE_PLUS/V1/S2_HARMONIZED');

// Get the band names from the first image in the Cloud Score Plus collection.
var csPlusBands = csPlus.first().bandNames();

// **Step 2: Cloud Masking**

// Merge the Sentinel-2 images with their corresponding Cloud Score Plus images.
var filteredS2WithCs = filtered.linkCollection(csPlus, csPlusBands);

// Define a function to mask out pixels with low cloud quality scores.
function maskLowQA(image) {
  var qaBand = 'cs';  // Cloud score band from CS+
  var clearThreshold = 0.5;  // Threshold to determine "clear" pixels.
  var mask = image.select(qaBand).gte(clearThreshold); // Keep pixels with high clarity.
  return image.updateMask(mask); // Apply the mask.
}

// Apply cloud masking to the Sentinel-2 images and retain only spectral bands (e.g., B2, B3, B4, etc.).
var filteredMasked = filteredS2WithCs
  .map(maskLowQA)
  .select('B.*');

// **Step 3: Generating Median Composites for January 2020 and January 2024**

// Filter Sentinel-2 images to keep only those from January 2020.
var filtered2020 = filteredMasked.filter(ee.Filter.date('2020-01-01','2020-02-01'));

// Compute the median composite for January 2020.
var image2020 = filtered2020.median();

// Display the 2020 image on the map.
Map.addLayer(image2020.clip(geometry), rgbVis, '2020');

// Repeat the process for January 2024.
var filtered2024 = filteredMasked.filter(ee.Filter.date('2024-01-01','2024-02-01'));
var image2024 = filtered2024.median();
Map.addLayer(image2024.clip(geometry), rgbVis, '2024');

// **Step 4: Stacking the 2020 and 2024 Images for Change Detection**
var stackedImage = image2020.addBands(image2024);

// **Step 5: Creating Training Data for Classification**
// Merge the "change" and "nochange" datasets to create a labeled training dataset.
var training = stackedImage.sampleRegions({
  collection: change.merge(nochange),  // Merging both classes for supervised classification.
  properties: ['class'],  // The property that contains class labels (change vs. no change).
  scale: 10  // Sampling at 10-meter resolution (Sentinel-2 native resolution).
});

// **Step 6: Training a Random Forest Classifier**
var classifier = ee.Classifier.smileRandomForest(50).train({
  features: training,  // Use the training dataset.
  classProperty: 'class',  // The property containing class labels.
  inputProperties: stackedImage.bandNames()  // Use all available bands as features.
});

// **Step 7: Classifying the Image**
var classified = stackedImage.classify(classifier);

// Display the classification result on the map.
// - Class 0 (No Change) → White
// - Class 1 (Change/New Buildings) → Red
Map.addLayer(classified.clip(geometry), {min: 0, max: 1, palette: ['white', 'red']}, 'Change Detection');
