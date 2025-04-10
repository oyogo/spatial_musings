import xarray as xr # working with multidimensional arrays and geospatial rasters.
import rioxarray # same as above
import geopandas as gpd
import numpy as np
from dask.diagnostics import ProgressBar # for parallel computing
from dask.distributed import Client
from rasterstats import zonal_stats
import requests
import os
import pandas as pd
from rasterio.enums import Resampling # raster resampling methods

# Set up Dask client (adjust memory and CPU usage)
def setup_dask():
    client = Client(memory_limit='16GB', n_workers=4, threads_per_worker=2, processes=True)
    return client

# Function to download admin level 3 boundaries from GitHub for specified country(using the 3 letter country code)
def fetch_admin3_boundaries(country_code='BGD', admin_level='ADM3'):
    geojson_url = f"https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/{country_code}/{admin_level}/geoBoundaries-{country_code}-{admin_level}_simplified.geojson"
    geojson_path = f"{country_code}_admin3_boundaries.geojson"
    
    response = requests.get(geojson_url)
    if response.status_code == 200:
        with open(geojson_path, 'wb') as f:
            f.write(response.content)
        print(f"Downloaded Admin Level 3 boundaries for {country_code}")
        return geojson_path
    else:
        raise Exception(f"Error: Could not fetch GeoJSON. Status Code: {response.status_code}")

# Function to match resolution, CRS, and extent of a raster to match that of the base raster (for folder_1 : those that need downsampling - Meta rasters)
def match_raster_to_base(raster_path, base_raster_path):
    base_raster = rioxarray.open_rasterio(base_raster_path).astype('float32') # by default it loads it as float64 which can be quite heavy
    raster = rioxarray.open_rasterio(raster_path).astype('float32')
    raster = raster.rio.reproject(base_raster.rio.crs) # align the crs of the raster with the base raster
    
    # Calculate the resolution differences of both rasters, determine the scaling factor to then align resolutions. 
    raster_resolution_x, raster_resolution_y = raster.rio.resolution()
    base_raster_resolution_x, base_raster_resolution_y = base_raster.rio.resolution()
    
    resampling_factor_x = int(raster_resolution_x / base_raster_resolution_x)
    resampling_factor_y = int(raster_resolution_y / base_raster_resolution_y)

    if resampling_factor_x > 0 and resampling_factor_y > 0:
        raster_resampled = raster.coarsen(x=resampling_factor_x, y=resampling_factor_y, boundary='trim').sum()
    else:
        raster_resampled = raster
    
    return raster_resampled

# Function to upsample rasters (nearest neighbor method) for folder_2
def upsample_raster(raster_path, base_raster_path):
    base_raster = rioxarray.open_rasterio(base_raster_path)
    raster = rioxarray.open_rasterio(raster_path)
    raster = raster.rio.reproject(base_raster.rio.crs)
    
    # Upsample using nearest neighbor method
    raster_resampled = raster.rio.reproject_match(base_raster, resampling=Resampling.nearest)
    
    return raster_resampled

# Function to calculate zonal statistics without chunking the raster
def calculate_zonal_stats(raster, admin3_geojson):
    admin3 = gpd.read_file(admin3_geojson)
    # Dissolve the GeoDataFrame by 'shapeName' to ensure single geometry per zone
    admin3 = admin3.dissolve(by='shapeName')

    if admin3.crs != raster.rio.crs:
        admin3 = admin3.to_crs(raster.rio.crs)

    raster_data = raster[0].data  # Assumes the first band (if multi-band data)
    raster_data = np.nan_to_num(raster_data, nan=0)  # Replace NaNs with 0
    transform = raster.rio.transform()
    
    stats = zonal_stats(admin3, raster_data, affine=transform, stats=['mean', 'sum', 'min', 'max', 'std','count'], nodata=0, geojson_out=True)
    
    # Convert the output to replace any NaN values in properties fields
    for entry in stats:
        for key, value in entry['properties'].items():
            if pd.isna(value):  # Check if value is NaN and replace it
                entry['properties'][key] = 0

    return stats

if __name__ == "__main__":
    # Set up Dask client
    client = setup_dask()

    # Define paths to folders and base raster
    base_raster_path = "C:\\Users\\cdavid\\OneDrive\\SAE_Guide_App\\data\\base_rasters\\BGD_level0_100m_2000_2020.tif"
    folder_1 = "C:\\Users\\cdavid\\OneDrive\\SAE_Guide_App\\data\\BGD\\folder_1"
    folder_2 = "C:\\Users\\cdavid\\OneDrive\\SAE_Guide_App\\data\\BGD\\folder_2"
    folder_3 = "C:\\Users\\cdavid\\OneDrive\\SAE_Guide_App\\data\\BGD\\folder_3"

    # Fetch the admin level 3 boundaries
    admin3_geojson = fetch_admin3_boundaries()
    
    # Initialize an empty DataFrame to hold combined zonal stats
    combined_zonal_stats_df = None

    # Define folders and respective functions to handle rasters
    folder_mapping = {
        folder_1: match_raster_to_base,
        folder_2: upsample_raster,
        folder_3: lambda x, y: rioxarray.open_rasterio(x)  # No transformation for folder_3 : WorldPop rasters
    }

    # Iterate through each folder and apply the correct transformation
    for folder, transformation_function in folder_mapping.items():
        for raster_file in os.listdir(folder):
            raster_path = os.path.join(folder, raster_file)

            # Apply the corresponding transformation function
            resampled_raster = transformation_function(raster_path, base_raster_path)

            # Compute the resampling with Dask and show progress
            with ProgressBar():
                resampled_raster = resampled_raster.compute()

            # Calculate zonal statistics
            zonal_statistics = calculate_zonal_stats(resampled_raster, admin3_geojson)

            # Convert zonal statistics (geojson_out) to a DataFrame
            zonal_stats_df = pd.json_normalize(zonal_statistics)

            # Extract relevant fields from the 'properties' field in the DataFrame
            zonal_stats_df = zonal_stats_df[['id', 'properties.mean', 'properties.sum', 'properties.min', 'properties.max', 'properties.std','properties.count']]

            # Fill NaN values with 0
            zonal_stats_df = zonal_stats_df.fillna(0)

            for col in ['properties.mean', 'properties.sum', 'properties.min', 'properties.max', 'properties.std','properties.count']:
                zonal_stats_df[col] = zonal_stats_df[col].astype(float).astype(int)

            # Get the raster file name (without extension) to append to column names
            raster_name = os.path.splitext(os.path.basename(raster_file))[0]

            # Rename columns by appending raster name
            zonal_stats_df.columns = ['Region'] + [f"{raster_name}.{stat}" for stat in ['mean', 'sum', 'min', 'max', 'std','count']]

            # Merge with the combined DataFrame, using 'Region' as the common column
            if combined_zonal_stats_df is None:
                combined_zonal_stats_df = zonal_stats_df
            else:
                combined_zonal_stats_df = pd.merge(combined_zonal_stats_df, zonal_stats_df, on='Region', how='outer')

    # Save the combined zonal statistics to a CSV file
    output_csv = "C:\\Users\\cdavid\\OneDrive\\SAE_Guide_App\\data\\BGD\\zonalstats\\BGD_adm3_zonalstats.csv"
    combined_zonal_stats_df.to_csv(output_csv, index=False) 

    print(f"Combined zonal statistics saved to: {output_csv}")

    # Close the Dask client after computation
    client.close()
