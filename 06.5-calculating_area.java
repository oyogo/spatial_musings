
// Load Kenya ward boundaries from a FeatureCollection
var county = ee.FeatureCollection('projects/sae-deep/assets/kenya_wards'); 

var classified = ee.ImageCollection('projects/sae-deep/kisii_lc_classification');

// Filter to select only features where the 'county' attribute is 'Kisii'
var kisii = county.filter(ee.Filter.eq('county', 'Kisii'));

var geometry = kisii.geometry();

var area = geometry.area(); // always in Sq. Meters
var areaSqKm = area.divide(1e6);

var vegetation = classified.eq(3);
Map.addLayer(vegetation)

// calculate area of each pixel
var areaImage = vegetation.multiply(ee.Image.pixelArea());

// sum the areas of all pixel in the region
var stats = areaImage.reduceRegion({
  reducer: ee.Reducer.sum(),
  geometry: geometry,
  scale: 10,
  maxPixels: 1e10,
  tileScale:16
});

print(stats.getNumber('classification').divide(1e6))

// Calculating area for all classes

var palette = ['#cc6d8f', '#ffc107', '#004d40' ];
Map.addLayer(classified, {min: 0, max: 2, palette: palette}, '2019');

// We can calculate the areas of all classes in a single pass
// using a Grouped Reducer. Learn more at 
// https://spatialthoughts.com/2020/06/19/calculating-area-gee/

// First create a 2 band image with the area image and the classified image
// Divide the area image by 1e6 so area results are in Sq Km
var areaImage = ee.Image.pixelArea().divide(1e6).addBands(classified);

// Calculate areas
var areas = areaImage.reduceRegion({
      reducer: ee.Reducer.sum().group({
      groupField: 1,
      groupName: 'classification',
    }),
    geometry: geometry,
    scale: 10,
    maxPixels: 1e10
    }); 
 
var classAreas = ee.List(areas.get('groups'))

// Process results to extract the areas and
// create a FeatureCollection

// We can define a dictionary with class names
var classNames = ee.Dictionary({
  '0': 'urban',
  '1': 'bare',
  '2': 'vegetation'
})

var classAreas = classAreas.map(function(item) {
  var areaDict = ee.Dictionary(item)
  var classNumber = ee.Number(areaDict.get('classification')).format();
  var className = classNames.get(classNumber);
  var area = ee.Number(
    areaDict.get('sum'))
  return ee.Feature(null, {'class': classNumber, 'class_name': className, 'area': area})
})

var classAreaFc = ee.FeatureCollection(classAreas);

// We can now chart the resulting FeatureCollection
// If your area is large, it is advisable to first Export
// the FeatureCollection as an Asset and import it once
// the export is finished.
// Let's create a Bar Chart
var areaChart = ui.Chart.feature.byProperty({
  features: classAreaFc,
  xProperties: ['area'],
  seriesProperty: 'class_name',
}).setChartType('ColumnChart')
  .setOptions({
    hAxis: {title: 'Classes'},
    vAxis: {title: 'Area Km^2'},
    title: 'Area by class',
    series: {
      0: { color: '#cc6d8f' },
      1: { color: '#ffc107' },
      2: { color: '#004d40' }
    }
  });
  
print(areaChart); 