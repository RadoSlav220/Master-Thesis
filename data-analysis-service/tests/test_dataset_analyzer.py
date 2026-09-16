import pytest

from app.services import dataset_analyzer
from app.services.dataset_analyzer import MalformedFileError, UnsupportedFileError

CSV = b"timestamp,temperature,humidity\n1,20,50\n2,21,55\n"


def test_analyze_csv_returns_columns():
    result = dataset_analyzer.analyze("data.csv", CSV)
    assert result.datasetType == "CSV"
    assert result.columns == ["timestamp", "temperature", "humidity"]
    assert result.properties is None


def test_analyze_unsupported_extension_raises():
    with pytest.raises(UnsupportedFileError):
        dataset_analyzer.analyze("data.txt", b"hello")


def test_analyze_geojson_extension_now_unsupported():
    with pytest.raises(UnsupportedFileError):
        dataset_analyzer.analyze("data.geojson", b'{"type":"FeatureCollection","features":[]}')


def test_analyze_malformed_csv_raises():
    with pytest.raises(MalformedFileError):
        dataset_analyzer.analyze("data.csv", b"")
