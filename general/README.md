# General Tasks #

Project intended as a home for tasks not currently in suites. They may move or _graduate_ as other tasks developed. Note that unlike the tasks bundled with DSpace (also in the ctask.general package), these tasks may have external dependencies.

## Task Descriptions ##

### MetadataWebService ###

MetadataWebService task calls a web service using metadata from passed item to obtain data. Depending on configuration, this data may be assigned to item metadata fields, or just recorded in the task result string. Task succeeds if web service call succeeds and configured updates occur, fails if task user not authorized or item lacks metadata to call service, and returns error in all other cases (except skip status for non-item objects). 
 
Intended use: cataloging tool in workflow and general curation. The task uses a URL 'template' to compose the service call, e.g.
 
    http://www.sherpa.ac.uk/romeo/api29.php?issn={dc.identifier.issn}
 
Task will substitute the value of the passed item's metadata field in the {parameter} position. If multiple values are present in the item field, the first value is used.
 
The task also uses a property (the datamap) to determine what data to extract from the service response and how to use it, e.g.
 
    //publisher/name=>dc.publisher,//romeocolour
 
Task will evaluate the left-hand side (or entire token) of each comma-separated token in the property as an XPath 1.0 expression into the response document, and if there is a mapping symbol (e.g.'=>') and value, it will assign the response document value(s) to the named metadata field in the passed item. If the response document contains multiple values, they will all be assigned to the item field. The mapping symbol governs the nature of metadata field assignment:
 
 * '->' mapping will add to any existing values in the item field
 * '=>' mapping will replace any existing values in the item field
 * '~>' mapping will add *only* if item field has no existing values
 
Unmapped data (without a mapping symbol) will simply be added to the task result string, prepended by the XPath expression (a little prettified).
A very rudimentary facility for transformation of data is supported, e.g.
 
    http://www.crossref.org/openurl/?id={doi:dc.relation.isversionof}&format=unixref
 
The 'doi:' prefix will cause the task to look for a 'transform' with that name, which is applied to the metadata value before parameter substitution occurs. Transforms are defined in a task property such as the following:

    transform.doi = match 10. trunc 60

This means exclude the value string up to the occurrence of '10.', then truncate after 60 characters. The only transform functions currently defined:

 * 'cut' <number> = remove number leading characters
 * 'trunc' <number> = remove trailing characters after number length
 * 'match' <pattern> = start match at pattern

If the transform results in an invalid state (e.g. cutting more characters than are in the value), the condition will be logged and the un-transformed value used.
 
Transforms may also be used in datamaps, e.g.
 
    //publisher/name=>shorten:dc.publisher,//romeocolour
  
which would apply the 'shorten' transform to the service response value(s) prior to metadata field assignment.

As with all 'profiled' tasks, configuration files live in config/modules using the task name.

