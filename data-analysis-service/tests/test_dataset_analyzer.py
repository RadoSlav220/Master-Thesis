import pytest

from app.services import dataset_analyzer
from app.services.dataset_analyzer import MalformedFileError, UnsupportedFileError

GEOJSON = (
    b'{"type":"FeatureCollection","features":['
    b'{"type":"Feature","properties":{"pm25":33,"name":"A"},'
    b'"geometry":{"type":"Point","coordinates":[23.32,42.69]}},'
    b'{"type":"Feature","properties":{"congestion":0.5},'
    b'"geometry":{"type":"Point","coordinates":[23.33,42.70]}}]}'
)

CSV = b"timestamp,temperature,humidity\n1,20,50\n2,21,55\n"


def test_analyze_csv_returns_columns():
    result = dataset_analyzer.analyze("data.csv", CSV)
    assert result.datasetType == "CSV"
    assert result.columns == ["timestamp", "temperature", "humidity"]
    assert result.properties is None


def test_analyze_geojson_returns_union_of_property_keys():
    result = dataset_analyzer.analyze("data.geojson", GEOJSON)
    assert result.datasetType == "GEOJSON"
    # Union across features, first-seen order.
    assert result.properties == ["pm25", "name", "congestion"]
    assert result.columns is None


def test_analyze_unsupported_extension_raises():
    with pytest.raises(UnsupportedFileError):
        dataset_analyzer.analyze("data.txt", b"hello")


def test_analyze_malformed_geojson_raises():
    with pytest.raises(MalformedFileError):
        dataset_analyzer.analyze("data.geojson", b'{"type":"nope"}')


def test_analyze_malformed_csv_raises():
    with pytest.raises(MalformedFileError):
        dataset_analyzer.analyze("data.csv", b"")
