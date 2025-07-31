# Updated script to handle optional "band" and "year" in raster metadata
import ee
import pandas as pd
from concurrent.futures import ThreadPoolExecutor
import requests
import json
import geemap


service_account = 'service.account.email'
credentials = ee.ServiceAccountCredentials(service_account, 'service.account.json')
# Initialize Earth Engine with service account credentials
ee.Initialize(credentials, project="your-project")

# Define the region of interest (ROI)
country = 'Ethiopia'
roi = ee.FeatureCollection("FAO/GAUL/2015/level0").filter(ee.Filter.eq('ADM0_NAME', country))

# Fetch shapefile for admin level 3 from GitHub
url = 'https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/ETH/ADM3/geoBoundaries-ETH-ADM3_simplified.geojson'
response = requests.get(url)
geojson_data = response.json()
admin_level_three = geemap.geojson_to_ee(geojson_data)

# Dictionary of rasters to process with their corresponding resampling methods, and bands.
raster_ids = [
    {"id": "projects/your-project/assets/ETH/worldPop_age_categories_2020","band": "children_under_5"},
    {"id": "projects/your-project/assets/ETH/worldPop_age_categories_2020","band": "youth_15_24"},
    {"id": "projects/your-project/assets/ETH/worldPop_age_categories_2020","band": "all_men"},
    {"id": "projects/your-project/assets/ETH/worldPop_age_categories_2020","band": "women_reproductive_15_49"},
    {"id": "projects/your-project/assets/ETH/worldPop_age_categories_2020","band": "all_women"},
    {"id": "projects/your-project/assets/ETH/worldPop_age_categories_2020","band": "elderly_60_plus"},
    {"id": "projects/your-project/assets/ETH/ETH_dst_coastline_100m_2000_2020"},
    {"id": "projects/your-project/assets/ETH/ETH_osm_dist_road_100m_2016"},
    {"id": "projects/your-project/assets/ETH/ETH_osm_dist_roadintersec_100m_2016"},
    {"id": "projects/your-project/assets/ETH/ETH_osm_dist_waterway_100m_2016"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst_water_100m_2000_2012"},
    {"id": "projects/your-project/assets/ETH/ETH_srtm_slope_100m"},
    {"id": "projects/your-project/assets/ETH/ETH_srtm_topo_100m"},
    {"id": "projects/your-project/assets/ETH/ETH_wdpa_dst_cat1_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst011_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst040_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst130_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst140_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst150_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst160_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst190_100m_2014"},
    {"id": "projects/your-project/assets/ETH/ETH_esaccilc_dst200_100m_2014"},
    {"id": "Oxford/MAP/accessibility_to_cities_2015_v1_0", "resample_method": "bilinear"},
    {"id": "NOAA/VIIRS/DNB/ANNUAL_V21", "resample_method": "bilinear", "band": "average", "year": "2020"}#,
    #{"id": "Oxford/MAP/accessibility_to_healthcare_2019", "resample_method": "bilinear", "band": "accessibility"}, # this images are corrupted
    #{"id": "Oxford/MAP/accessibility_to_healthcare_2019", "resample_method": "bilinear", "band": "accessibility_walking_only"}
]

# Define the base raster for resampling
base_raster_id = "projects/your-project/assets/base_layers/ETH_baselayer"
base_raster = ee.Image(base_raster_id)

# Function to process and resample raster based on method
def clip_and_resample_raster(raster_info, base_raster):
    # Determine image source type
    if "year" in raster_info:
        image = ee.ImageCollection(raster_info["id"])

        # Apply date filtering if year is provided
        if "year" in raster_info:
            start = f"{raster_info['year']}-01-01"
            end = f"{int(raster_info['year']) + 1}-01-01"
            image = image.filterDate(start, end)

        # Reduce to first image in collection
        image = image.first()

        # Select band if specified
        if "band" in raster_info:
            image = image.select(raster_info["band"])
    else:
        image = ee.Image(raster_info["id"])

    # Clip to region of interest
    clipped_image = image.clip(roi)

    # Apply resampling if method is provided
    if "resample_method" in raster_info:
        method = raster_info["resample_method"]
        if method == "bilinear":
            resampled_image = clipped_image.resample('bilinear').reproject(
                crs=base_raster.projection(),
                scale=base_raster.projection().nominalScale()
            )
        elif method == "sum":
            resampled_image = clipped_image.reduceResolution(
                reducer=ee.Reducer.sum()
            ).reproject(
                crs=base_raster.projection(),
                scale=base_raster.projection().nominalScale()
            )
        else:
            raise ValueError(f"Unknown resample method: {method}")
        return resampled_image
    else:
        return clipped_image

# Function to calculate zonal statistics
def calculate_zonal_stats(raster_info):
    image = clip_and_resample_raster(raster_info, base_raster)
    stats = image.reduceRegions(
        collection=admin_level_three,
        reducer=ee.Reducer.mean().combine(
            reducer2=ee.Reducer.min(), sharedInputs=True
        ).combine(
            reducer2=ee.Reducer.max(), sharedInputs=True
        ).combine(
            reducer2=ee.Reducer.stdDev(), sharedInputs=True
        ).combine(
            reducer2=ee.Reducer.sum(), sharedInputs=True
        ).combine(
            reducer2=ee.Reducer.count(), sharedInputs=True
        ),
        scale=100
    ).getInfo()

    # Extract data and convert to DataFrame
    rows = []
    for feature in stats['features']:
        properties = feature['properties']
        properties['ADM3'] = properties['shapeName']
        rows.append(properties)

    df = pd.DataFrame(rows)
    df = df.add_prefix(f"{raster_info['id'].split('/')[-1]}.")
    df = df.rename(columns={f"{raster_info['id'].split('/')[-1]}.ADM3": "ADM3"})
    df = df[df.columns[df.columns.str.contains('min|max|stdDev|sum|count|mean|ADM3')]]
    return df

# Use ThreadPoolExecutor to process rasters in parallel
with ThreadPoolExecutor() as executor:
    results = list(executor.map(calculate_zonal_stats, raster_ids))


# Combine results into one DataFrame
def get_df():
    final_df = pd.concat(results, axis=1)
    final_df = final_df.loc[:, ~final_df.columns.duplicated()]
    final_df = final_df.reset_index(drop=True)
    final_df.to_csv('ETH_GEE_zonal_statistics_v3.csv', index=False)

get_df()
