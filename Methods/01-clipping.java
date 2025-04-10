
// Some rasters can be quite huge, especially if they cover the entire world and span several years. For such you will need to cut the raster to a region of interest.
// Modify this script to clip any raster to a region of interest. 


// Load sentinel2 harmonized image collection : multispectral data at high resolution
var s2 = ee.ImageCollection('COPERNICUS/S2_HARMONIZED');

// You could optionally load FAO's simplified adminstrative boundaries at admin2:   var urban = ee.FeatureCollection('FAO/GAUL_SIMPLIFIED_500m/2015/level2');
// For this case, we're loading custom featurecollection uploaded to my google earth engine project.
var county = ee.FeatureCollection('projects/sae-deep/assets/kenya_wards'); 

// Filter by the county FeatureCollection to select feature where county == Kisii. You could choose any level as is available in your shapefile (FeatureCollection)
var filtered = county.filter(ee.Filter.eq('county', 'Kisii'));

//Extract the geometry of the filtered feature (boundaries of Kisii county)
var geometry = filtered.geometry();

// Center the map view on the geometry of the region of interest - Kisii county.
Map.centerObject(geometry); 

// Visualization parameters for displaying RGB imagery.
// - `min` and `max`: the range of pixel values for visualization.
// - `bands`: the RGB bands (B4 = red, B3 = green, B2 = blue).
var rgbVis = {
  min: 0.0,
  max: 3000,
  bands: ['B4', 'B3', 'B2'], // Ease identification of natural features like vegetation, water, bodies and urban areas. 
};

// Filter the Sentinel-2 image collection to:
// 1. Include images with less than 30% cloud cover.cover.
// 2. Restrict the date range to images from 2019.
// 3. Restrict spatial coverage to the geometry of Kisii county. 

var filtered = s2.filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .filter(ee.Filter.date('2019-01-01', '2020-01-01'))
  .filter(ee.Filter.bounds(geometry));

// Compute the median of the filtered image collection to create a single representative image. 
// The median reduces noise and cloud contamination by aggregating pixel values. 
var image = filtered.median(); 

// Clip the median image to the geometry of Kisii county. 
// This ensures that only pixels within Kisii county's boundaries are retained. 
var clipped = image.clip(geometry);

// Add the clipped image layer to the map with the specified RGB visualization parameters. 
Map.addLayer(clipped, rgbVis, 'Clipped');

//If you want to export the image to your google drive. 
var exportImage = clipped.select('B.*');

// Export raw image with original pixel values
Export.image.toDrive({
    image: exportImage,
    description: 'Kisii_county',
    folder: 'earthengine',
    fileNamePrefix: 'kisii_county',
    region: geometry,
    scale: 10,
    maxPixels: 1e9
});