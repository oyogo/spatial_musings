## 🌍Geospatial Projects and Scripts Repository         

Welcome to my geospatial repository - a curated collection of scripts and projects that showcase various geospatial data processing and analysis concepts using tools like Google Earth Engine (GEE), R, and Python geospatial libraries. Whether you're a researcher, student, or practitioner, this repository provides working examples for common spatial analysis tasks such as satellite imagery clipping, raster processing, spatial statistics, and visualization.     

Each folder contains purpose-driven scripts tackling specific geospatial challenges, with clear documentation and reproducible workflows. The goal is to provide a hands-on reference for using open geospatial data to solve real-world problems across sectors such as, Agriculture, Urban planning, and Humanitarian work.    

### 📊 Handling Huge Raster Files     

Working with multiple high-resolution raster datasets - especially those spanning long time periods or covering entire countries - can quickly overwhelm memory, crash your scripts, or result in painfully slow processing times. This is particularly true when trying to extract zonal statistics across thousands of administrative units. The **extract_zonalstatistics.py** script tackles those challenges head-on by leveraging **xarray** and **rioxarray** for efficient raster handling, combined with the parallel computing power of **Dask**. It intelligently resamples, reprojects, and aligns rasters from various sources (including downsampling/upsampling as needed) and then applies zonal statistics at the admin-3 level using rasterstats. By chunking and distributing the workload, the script makes it feasible to compute stats across massive geospatial datasets and consolidate them into a clean, analysis-ready CSV. This approach ensures performance, scalability, and consistency across raster inputs.     



