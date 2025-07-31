import ee
import geemap

# Initialize with service account
service_account = 'service.account.email'
credentials = ee.ServiceAccountCredentials(service_account, 'service.account.json')
ee.Initialize(credentials, project="your-project")

# Define region of interest
country = 'Ethiopia'
country_code = 'ETH'  # ISO 3-letter code
roi = ee.FeatureCollection("FAO/GAUL/2015/level0").filter(ee.Filter.eq('ADM0_NAME', country))

def create_worldpop_age_categories(year=2020, region=roi):
    """
    Create age category composites from WorldPop age/sex disaggregated data, clipped to region.
    """
    
    # Load and clip WorldPop data
    worldpop = ee.ImageCollection('WorldPop/GP/100m/pop_age_sex') \
                 .filter(ee.Filter.eq('year', year)) \
                 .mosaic() \
                 .clip(region)
    
    # Define categories
    children_under_5 = worldpop.select(['F_0', 'F_1', 'M_0', 'M_1']).reduce(ee.Reducer.sum()).rename('children_under_5')
    youth_15_24 = worldpop.select(['F_15', 'F_20', 'M_15', 'M_20']).reduce(ee.Reducer.sum()).rename('youth_15_24')
    all_men = worldpop.select(['M_0', 'M_1', 'M_5', 'M_10', 'M_15', 'M_20', 'M_25', 'M_30', 'M_35',
                               'M_40', 'M_45', 'M_50', 'M_55', 'M_60', 'M_65', 'M_70', 'M_75', 'M_80']) \
                      .reduce(ee.Reducer.sum()).rename('all_men')
    women_reproductive = worldpop.select(['F_15', 'F_20', 'F_25', 'F_30', 'F_35', 'F_40', 'F_45']) \
                                 .reduce(ee.Reducer.sum()).rename('women_reproductive_15_49')
    all_women = worldpop.select(['F_0', 'F_1', 'F_5', 'F_10', 'F_15', 'F_20', 'F_25', 'F_30', 'F_35',
                                 'F_40', 'F_45', 'F_50', 'F_55', 'F_60', 'F_65', 'F_70', 'F_75', 'F_80']) \
                        .reduce(ee.Reducer.sum()).rename('all_women')
    elderly_60_plus = worldpop.select(['F_60', 'F_65', 'F_70', 'F_75', 'F_80',
                                       'M_60', 'M_65', 'M_70', 'M_75', 'M_80']) \
                              .reduce(ee.Reducer.sum()).rename('elderly_60_plus')

    # Combine and tag
    age_categories = ee.Image.cat([
        children_under_5,
        youth_15_24,
        all_men,
        women_reproductive,
        all_women,
        elderly_60_plus
    ]).set({
        'year': year,
        'country': country,
        'country_code': country_code,
        'bands': 'children_under_5,youth_15_24,all_men,women_reproductive_15_49,all_women,elderly_60_plus',
        'source': 'WorldPop_GP_100m_pop_age_sex'
    })
    
    return age_categories

def export_age_categories_to_asset(year=2020, region=roi, country_code='ETH'):
    """
    Export age category composites clipped to region to GEE Assets
    """
    age_categories = create_worldpop_age_categories(year=year, region=region)
    
    asset_id = f'projects/your-project/assets/{country_code}/worldPop_age_categories_{year}'
    print(f"Exporting to asset: {asset_id}")
    
    export_params = {
        'image': age_categories,
        'description': f'{country_code}_WorldPop_Age_Categories_{year}',
        'assetId': asset_id,
        'scale': 100,
        'region': region.geometry(),
        'maxPixels': 1e13,
        'pyramidingPolicy': {'.default': 'mean'}
    }

    task = ee.batch.Export.image.toAsset(**export_params)
    task.start()
    
    print(f"Export started for {country} ({year}) → {asset_id}")
    print("Monitor in Earth Engine > Tasks")

    return task

if __name__ == "__main__":
    print("\n▶ Starting export for Ethiopia (2020)...")
    export_age_categories_to_asset(year=2020, region=roi, country_code=country_code)
