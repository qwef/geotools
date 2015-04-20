/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2015, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.gce.imagemosaic.catalog;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.geotools.data.DataUtilities;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.gce.imagemosaic.Utils;
import org.geotools.util.Converters;
import org.opengis.filter.Filter;

/**
 * Factory class used for returning a {@link MultiLevelROIProvider} based on the input footprint properties
 * and files
 * 
 * @author Andrea Aime GeoSolutions
 * @author Nicola Lagomarsini GeoSolutions
 */
public class MultiLevelROIProviderFactory {

    // well known properties
    public static final String SOURCE_PROPERTY = "footprint_source";

    public static final String FILTER_PROPERTY = "footprint_filter";

    public static final String INSET_PROPERTY = "footprint_inset";

    public static final String INSET_TYPE_PROPERTY = "footprint_inset_type";

    // store types
    private static final String TYPE_SIDECAR = "sidecar";
    
    private static final String TYPE_RASTER = "raster";

    private MultiLevelROIProviderFactory() {
    }

    /**
     * Builds a footprint provider from mosaic location
     * 
     * @param mosaicFolder The folder that contains the mosaic config files
     * @return
     * @throws Exception
     */
    public static MultiLevelROIProvider createFootprintProvider(File mosaicFolder) {
        File configFile = new File(mosaicFolder, "footprints.properties");
        final Properties properties;
        if (configFile.exists()) {
            properties = Utils.loadPropertiesFromURL(DataUtilities.fileToURL(configFile));
        } else {
            properties = new Properties();
        }

        // load the type of config file
        String source = (String) properties.get(SOURCE_PROPERTY);
        FootprintGeometryProvider provider = null;
        if (source == null) {
            // see if we have the default whole mosaic footprint
            File defaultShapefileFootprint = new File(mosaicFolder, "footprints.shp");
            if (defaultShapefileFootprint.exists()) {
                provider = buildShapefileSource(mosaicFolder, defaultShapefileFootprint.getName(),
                        properties);
            } else {
                provider = new SidecarFootprintProvider(mosaicFolder);
            }
        } else if (TYPE_SIDECAR.equals(source)) {
            provider = new SidecarFootprintProvider(mosaicFolder);
        } else if (source.toLowerCase().endsWith(".shp")) {
            provider = buildShapefileSource(mosaicFolder, source, properties);
        } else if(TYPE_RASTER.equals(source)){
            // Raster masking
            return new MultiLevelROIRasterProvider(mosaicFolder);
        } else {
            throw new IllegalArgumentException("Invalid source type, it should be a reference "
                    + "to a shapefile or 'sidecar', but was '" + source + "' instead");
        }
        
        // Create the provider
        // handle inset if necessary
        double inset = getInset(properties);
        FootprintInsetPolicy insetPolicy = getInsetPolicy(properties);
        return new MultiLevelROIGeometryProvider(provider, inset, insetPolicy);
    }

    private static FootprintInsetPolicy getInsetPolicy(Properties properties) {
        String insetTypeValue = (String) properties.get(INSET_TYPE_PROPERTY);
        if (insetTypeValue == null || insetTypeValue.trim().isEmpty()) {
            return FootprintInsetPolicy.border;
        } else {
            try {
                return FootprintInsetPolicy.valueOf(insetTypeValue.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid inset type '" + insetTypeValue
                        + "', valid values are: " + FootprintInsetPolicy.names());
            }
        }
    }

    private static double getInset(Properties properties) {
        String inset = (String) properties.get(INSET_PROPERTY);
        if (inset == null) {
            return 0;
        }
        Double converted = Converters.convert(inset, Double.class);
        if (converted == null) {
            throw new IllegalArgumentException("Invalid inset value, should be a "
                    + "floating point number, but instead it is: '" + inset + "'");
        }
        return converted;
    }

    private static FootprintGeometryProvider buildShapefileSource(File mosaicFolder, String location,
            Properties properties) {
        File shapefile = new File(location);
        if (!shapefile.isAbsolute()) {
            shapefile = new File(mosaicFolder, location);
        }

        try {
            if (!shapefile.exists()) {
                throw new IllegalArgumentException("Tried to load the footprints from "
                        + shapefile.getCanonicalPath() + " but the file was not found");
            } else {
                final Map<String, Serializable> params = new HashMap<String, Serializable>();
                params.put("url", DataUtilities.fileToURL(shapefile));
                String cql = (String) properties.get(FILTER_PROPERTY);
                Filter filter = null;
                if (cql != null) {
                    filter = ECQL.toFilter(cql);
                } else {
                    filter = ECQL.toFilter("location = granule.location");
                }
                String typeName = shapefile.getName();
                typeName = typeName.substring(0, typeName.lastIndexOf('.'));
                return new GTDataStoreFootprintProvider(params, typeName, filter);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to create a shapefile based footprint provider", e);
        }
    }

}
