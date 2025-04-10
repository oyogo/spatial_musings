// -----------------------------------------------------------------------------
// **Mosaicing and Composite**
//
// **Mosaicing:** This process stitches multiple overlapping satellite images 
//   into a single image. It prioritizes the topmost image in overlapping 
//   regions, often based on image order or quality. Useful when creating large 
//   area coverage without visible seams.
//
// **Composite:** A composite image combines multiple images by applying statistical 
//   operations (like median, mean, etc.) on each pixel across the image stack. 
//   This helps reduce noise (e.g., clouds) and enhances features by aggregating 
//   data over time.
//
// Mosaicing is great for visualizing large continuous areas quickly, while composites 
// are better for reducing anomalies like clouds and getting more stable, long-term 
// representations of the Earth's surface.
// -----------------------------------------------------------------------------

// Define a point of interest Nairobi
var geometry = ee.Geometry.Point([36.8200253, -1.28333]); 

// Load the Sentinel-2 Harmonized Image Collection
var s2 = ee.ImageCollection('COPERNICUS/S2_HARMONIZED');

// Apply filters to the Sentinel-2 collection:
// 1. Filter images with less than 30% cloud cover
// 2. Filter images within the year 2019
// 3. Filter images that intersect with the Nairobi geometry
var filtered = s2.filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .filter(ee.Filter.date('2019-01-01', '2020-01-01'))
  .filter(ee.Filter.bounds(geometry));
 
// **Mosaic:** Combine overlapping images into one seamless image.
// In overlapping areas, the top image (usually the most recent) is shown.
var mosaic = filtered.mosaic();
 
// **Median Composite:** Calculate the median pixel value for each band 
// across all filtered images. This helps reduce the impact of clouds 
// and transient anomalies, providing a cleaner image.
var medianComposite = filtered.median();

// Center the map on Nairobi with a zoom level of 10
Map.centerObject(geometry, 10);

// Visualization parameters for RGB (True Color) display
var rgbVis = {
  min: 0.0,
  max: 3000,
  bands: ['B4', 'B3', 'B2'], // Red, Green, Blue bands
};

// Add different layers to the map for comparison:
// 1. The filtered collection (all individual images)
// 2. The mosaic image (stitched seamless image)
// 3. The median composite (cloud-reduced composite image)
Map.addLayer(filtered, rgbVis, 'Filtered Collection');
Map.addLayer(mosaic, rgbVis, 'Mosaic');
Map.addLayer(medianComposite, rgbVis, 'Median Composite');
