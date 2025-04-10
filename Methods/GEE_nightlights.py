import ee 
import geopandas as gpd
import requests
import json
import geemap.foliumap as geemap

service_account = 'xxx@yyy.iam.gserviceaccount.com'
credentials = ee.ServiceAccountCredentials(service_account,'xxx.json')
# Initialize Earth Engine with service account credentials
ee.Initialize(credentials,project="sae-deep")

def fetch_geojson_or_image(country_code,img_dat,fetch_type):
    """ 
    Constructs url to fetch ADM3 boundaries from github repo or just downloads the raster image

    parameters:
    country_code (str): this is fed from R Shiny's selectinput variable (its reactive)
    img_dat (str): identifier for the earth engine image collection.
    fetch_type (str): type of data to fetch, if the raster is already on the server, just fetch the geojson for mapping.
    
    returns:
    either a url construct for fetching adm2 geojson or download the raster file which is then accessed for display on the map.
    """
    if fetch_type == 'geojson':
        url_construct = f"https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/{country_code}/ADM0/geoBoundaries-{country_code}-ADM0_simplified.geojson"
        return url_construct
    elif fetch_type == 'image':
        url_construct = f"https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/{country_code}/ADM0/geoBoundaries-{country_code}-ADM0_simplified.geojson"
        response = requests.get(url_construct)
        geojson_data = response.json()

        roi = geemap.geojson_to_ee(geojson_data) # getting the region of interest geometry

        nightlights = ee.ImageCollection(f"{img_dat}").filterDate('2015-01-01','2023-12-31').filterBounds(roi).select('avg_rad') # filtering dates and layer

        nightlightsClipped = nightlights.first().clip(roi.geometry()) # cliping to the region of interest

        # Download image
        url = nightlightsClipped.getDownloadUrl({
        'region':roi.geometry(),
        'scale':500,
        'format': 'GEO_TIFF',
        'maxPixels': 1e13
        })

        response = requests.get(url)
        with open(f'./data/downloads/ETH/folder_2/{country_code}_nightlights_2015.tif','wb') as file:
            file.write(response.content)
    else:
        raise ValueError("Invalid fetch_type. Must be either 'geojson' or 'image'")

fetch_geojson_or_image('ETH','NOAA/VIIRS/DNB/MONTHLY_V1/VCMSLCFG','image')