import json

import pytest

from app.services import dataset_filter
from app.services.dataset_filter import UnsupportedFileError

GEOJSON = (
    b'{"type":"FeatureCollection","features":['
    b'{"type":"Feature","properties":{"pm25":33,"name":"A","station":"S1"},'
    b'"geometry":{"type":"Point","coordinates":[23.32,42.69]}},'
    b'{"type":"Feature","properties":{"pm25":21,"name":"B","station":"S2"},'
    b'"geometry":{"type":"Point","coordinates":[23.33,42.70]}},'
    b'{"type":"Feature","properties":{"pm25":45,"name":"C","station":"S3"},'
    b'"geometry":{"type":"Point","coordinates":[23.31,42.68]}}]}'
)

CSV = (
    b"timestamp,latitude,longitude,congestion\n"
    b"2026-07-01,42.7,23.3,0.5\n"
    b"2026-07-02,42.71,23.31,0.8\n"
    b"2026-07-03,42.69,23.32,0.2\n"
)


def test_filter_geojson_selects_properties_and_limits_features():
    dataset_type, content = dataset_filter.filter_dataset("d.geojson", GEOJSON, ["pm25"], 2)
    assert dataset_type == "GEOJSON"
    fc = json.loads(content)
    assert len(fc["features"]) == 2
    # Only the selected property remains; geometry is preserved.
    for feature in fc["features"]:
        assert set(feature["properties"].keys()) == {"pm25"}
        assert feature["geometry"]["type"] == "Point"


def test_filter_geojson_no_columns_keeps_all_properties():
    _, content = dataset_filter.filter_dataset("d.geojson", GEOJSON, [], None)
    fc = json.loads(content)
    assert len(fc["features"]) == 3
    assert set(fc["features"][0]["properties"].keys()) == {"pm25", "name", "station"}


def test_filter_csv_selects_columns_and_limits_rows():
    dataset_type, content = dataset_filter.filter_dataset(
        "d.csv", CSV, ["timestamp", "congestion"], 2
    )
    assert dataset_type == "CSV"
    lines = content.strip().splitlines()
    assert lines[0] == "timestamp,congestion"
    assert len(lines) == 3  # header + 2 rows


def test_filter_unsupported_extension_raises():
    with pytest.raises(UnsupportedFileError):
        dataset_filter.filter_dataset("d.txt", b"x", [], None)
