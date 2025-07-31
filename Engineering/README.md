## Handling Huge Raster Files    

Working with multiple high-resolution raster datasets especially those spanning long time periods or covering entire countries, can quickly overwhelm memory, crash scripts, or result in painfully slow processing times. This challenge becomes even more pronounced when extracting zonal statistics across thousands of administrative units.    

The [`extracting_zonalstatistics_xarray.py`]() script addresses these challenges by combining:    

* **xarray** and **rioxarray** for efficient raster handling    
* **Dask** for parallel computing and out-of-core processing    

It intelligently **resamples, reprojects, and aligns** rasters from various sources (including downsampling/upsampling when necessary), then applies zonal statistics at the **admin-3** level using `rasterstats`. By chunking and distributing the workload, this approach makes it feasible to process massive geospatial datasets and produce a clean, analysis-ready CSV — all while maintaining performance, scalability, and consistency.     

> **Note:** This workflow is designed for **offline computation**.     

---

### Google Earth Engine (GEE) Approach     

The [`extracting_zonalstatistics_GEE.py`]() script takes a different route, offloading computation to **Google Earth Engine (GEE)**. This often results in faster processing, as both computation and raster storage are handled on GEE’s infrastructure.     

Like the xarray workflow, it **resamples, reprojects, selects bands**, and applies zonal statistics at the **admin-3** level using Earth Engine’s reducer functions.    

A key difference is that this script uses **WorldPop population density** data directly from the GEE data catalogue. Uploading meta population density rasters was impractical, so I opted for the readily available WorldPop dataset. These rasters have **global coverage**, with bands representing specific age groups.      

To process population data for a specific **Region of Interest (ROI)**, the [`process_worldpop_popdensity_GEE.py`]() script generates **single-band rasters** for defined age categories, then exports them to your **GEE assets** for further use.       

---

## Fetching Rasters for a Region of Interest

If you want to retrieve a raster masked to a specific ROI, the [`fetching_nightlights_GEE.py`]() script can be adapted to work with **any dataset available in the GEE catalogue**.   

For defining ROIs, I use country boundary GeoJSON files from the [geoBoundaries](https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/) dataset, referenced by their respective country codes.       

---

### Requirements for Using GEE Scripts      

To run any GEE-based workflow, you’ll need:     
  
1. A **Google account**     
1. A **Google Earth Engine** account       
2. A **service account** for your GEE project     
3. The **JSON key file** and **the email** for that service account       
