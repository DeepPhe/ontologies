# **tranSMART mapping specs**

The following mapping files were created based upon the standard tranSMART data mapping specifications used to load clinical data via the ETL scripts. in particular, these files were developed to load clinical TCGA breast data based on the data position contained within the clinical biotab file [see the TCGA biotab spec](https://wiki.nci.nih.gov/display/TCGA/Biotab).

**Required fields for ETL scripts** - The following fields must be present within the specification file:
- **filename**- the name of the file which contains the data
- **category code** - represents the categorical placement of the data within the ontology tree hierarchy; a plus sign is used to separate the path nodes. eg Breast+Outcomes
- **column number** - specified the column position of the data within the data file
- **data label** - used as a pretty label within the ontology tree hierarchy

**Optional fields**, used for reference purposes

- **data label source** - used as reference back to the source pretty label
- **controlled vocab code** - reference to a vocab code (e.g., LOINC); not used currently by the ETL scripts at this time
- **source column** - the name or field name from the source system,  i.e. biotab column headers
- **source column alternative** - an alternative name or field name from the source system (used by TCGA)
- **cadsr CDE id** - stores the associated public ID from the [caDSR](https://cdebrowser.nci.nih.gov/CDEBrowser/)

**DeepPhe model specific fields**, the following fields are specific to DeepPhe used for programmatic operations
- **class** - class name from the DeepPhe model
- **required avs** - relationship from DeepPhe model
- **property to return value** - return structure from DeepPhe model

### File Specifications
- **transmart-tcga-mapping-spec.txt** - contains the mapping definitions for TCGA breast clinical biotab data
- **CancerReg-tcga-mapping-spec.txt** - contains mapping definitions for a specific extracted data file from the cancer registry (non-NAACCR format)
- **CancerReg-tcga-PV-mapping-spec.txt** - contains definitions for mapping permissible values (PV) for specific extracted data file from the cancer registry to TCGA values


*See the [tranSMART ETL](https://github.com/transmart/tranSMART-ETL) for more info*
