// Spectral indices == ratio of 2 or more bands. Normalized difference is the most commonly used formula for calculating indices. 
// Note: for more complex formule, you can use the expression function. 

// Load the sentinel2 harmonized image collection. 
var s2 = ee.ImageCollection('COPERNICUS/S2_HARMONIZED');

// load admin2 from FAO's amdinstrative boundaries feature collection. See the clipping script if you want to use custom shapefiles. 
var admin2 = ee.FeatureCollection('FAO/GAUL_SIMPLIFIED_500m/2015/level2');

// Use this code to get the list of the names of admin(x) level. That's how I got to know that it's `Central Kisii` and not `Kisii` as I knew it. 
//var kenyaAdmin2 = admin2.filter(ee.Filter.eq('ADM0_NAME', 'Kenya'));

//var allCounties = kenyaAdmin2.aggregate_array('ADM2_NAME');
//print('All Counties in Kenya:', allCounties);

// Filter the feature collection to the region of interest - Kisii county
var kisii_county = admin2.filter(ee.Filter.eq('ADM2_NAME', 'Central Kisii'));

// extract the geometry and center the map to the region of interest. 
var geometry = kisii_county.geometry();
Map.centerObject(geometry);

// Filter less than 30% cloud cover, the year 2023 and restrict it to the region of interest. 
var filteredS2 = s2.filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .filter(ee.Filter.date('2023-01-01', '2024-01-01'))
  .filter(ee.Filter.bounds(geometry));

// Since it's an image collection, get the median to reduce them to a single representative image. 
var image = filteredS2.median(); 

// Calculate  Normalized Difference Vegetation Index (NDVI)
// 'NIR' (B8) and 'RED' (B4)
var ndvi = image.normalizedDifference(['B8', 'B4']).rename(['ndvi']);

// create the color palette for visualization
var ndviVis = {min:0, max:1, palette: ['white', 'green']};

// add the clipped NDVI image to the map
Map.addLayer(ndvi.clip(geometry), ndviVis, 'ndvi');