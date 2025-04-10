var s2 = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED');
// Load Kenya ward boundaries from a FeatureCollection
var county = ee.FeatureCollection('projects/sae-deep/assets/kenya_wards'); 

// Filter to select only features where the 'county' attribute is 'Kisii'
var geometry = county.filter(ee.Filter.eq('county', 'Kisii'));

// alternatively use the drawing tool to generate the gcps or import
var gcp = ee.FeatureCollection("projects/sae-deep/gcps_kisii");

Map.centerObject(geometry);

var rgbVis = {
  min: 0.0,
  max: 3000,
  bands: ['B4', 'B3', 'B2'],
};
 
var filtered = s2
.filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .filter(ee.Filter.date('2019-01-01', '2020-01-01'))
  .filter(ee.Filter.bounds(geometry))
  .select('B.*');

var composite = filtered.median();

// Display the input composite.
Map.addLayer(composite.clip(geometry), rgbVis, 'image');


// Add a random column and split the GCPs into training and validation set
var gcp = gcp.randomColumn();

// This being a simpler classification, we take 60% points
// for validation. Normal recommended ratio is
// 70% training, 30% validation
var trainingGcp = gcp.filter(ee.Filter.lt('random', 0.6));
var validationGcp = gcp.filter(ee.Filter.gte('random', 0.6));

// Overlay the point on the image to get training data.
var training = composite.sampleRegions({
  collection: trainingGcp,
  properties: ['landcover'],
  scale: 10,
  tileScale: 16
});

// Train a classifier.
var classifier = ee.Classifier.smileRandomForest(50)
.train({
  features: training,  
  classProperty: 'landcover',
  inputProperties: composite.bandNames()
});

// Classify the image.
var classified = composite.classify(classifier);

var palette = ['#cc6d8f', '#ffc107', '#1e88e5', '#004d40' ];
Map.addLayer(classified.clip(geometry), {min: 0, max: 3, palette: palette}, '2019');
//************************************************************************** 
// Accuracy Assessment
//************************************************************************** 

// Use classification map to assess accuracy using the validation fraction
// of the overall training set created above.
var test = classified.sampleRegions({
  collection: validationGcp,
  properties: ['landcover'],
  tileScale: 16,
  scale: 10,
});

var testConfusionMatrix = test.errorMatrix('landcover', 'classification')
// Printing of confusion matrix may time out. Alternatively, you can export it as CSV
print('Confusion Matrix', testConfusionMatrix);
print('Test Accuracy', testConfusionMatrix.accuracy());

// Alternate workflow 
// This is similar to machine learning practice
var validation = composite.sampleRegions({
  collection: validationGcp,
  properties: ['landcover'],
  scale: 10,
  tileScale: 16
});

var test = validation.classify(classifier);

var testConfusionMatrix = test.errorMatrix('landcover', 'classification')
// Printing of confusion matrix may time out. Alternatively, you can export it as CSV
print('Confusion Matrix', testConfusionMatrix);
print('Test Accuracy', testConfusionMatrix.accuracy());
Exercise