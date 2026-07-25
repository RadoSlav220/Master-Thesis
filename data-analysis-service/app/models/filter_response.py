from pydantic import BaseModel


class FilterResponse(BaseModel):
    """Filtered dataset content returned to the orchestrator.

    ``content`` is the filtered dataset as text (CSV) or a GeoJSON
    FeatureCollection JSON string, matching ``datasetType``.
    """

    datasetType: str
    content: str
