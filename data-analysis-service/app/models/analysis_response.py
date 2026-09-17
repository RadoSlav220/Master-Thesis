from typing import Optional

from pydantic import BaseModel


class AnalysisResponse(BaseModel):
    """Structure analysis of an uploaded dataset.

    For CSV datasets ``columns`` is populated.
    """

    datasetType: str
    columns: Optional[list[str]] = None
    properties: Optional[list[str]] = None
