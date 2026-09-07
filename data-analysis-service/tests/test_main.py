import json

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

GEOJSON = json.dumps(
    {
        "type": "FeatureCollection",
        "features": [
            {"type": "Feature", "properties": {"pm25": 33, "name": "A"},
             "geometry": {"type": "Point", "coordinates": [23.32, 42.69]}},
            {"type": "Feature", "properties": {"pm25": 21, "name": "B"},
             "geometry": {"type": "Point", "coordinates": [23.33, 42.70]}},
        ],
    }
).encode()


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "UP"}


def test_analyze_geojson_endpoint():
    resp = client.post("/analyze", files={"file": ("d.geojson", GEOJSON, "application/json")})
    assert resp.status_code == 200
    body = resp.json()
    assert body["datasetType"] == "GEOJSON"
    assert body["properties"] == ["pm25", "name"]


def test_analyze_bad_file_returns_400():
    resp = client.post("/analyze", files={"file": ("d.txt", b"hello", "text/plain")})
    assert resp.status_code == 400


STATION_CSV = (
    b"station,lat,lon,ts,pm25\n"
    b"S1,42.69,23.32,2024-01-01T00:00:00Z,12\n"
    b"S2,42.70,23.33,2024-01-01T00:00:00Z,9\n"
)

STATION_MAPPING = {
    "s.csv": {
        "station": "STATION_ID",
        "lat": "LATITUDE",
        "lon": "LONGITUDE",
        "ts": "TIMESTAMP",
        "pm25": "MEASUREMENT",
    }
}


def test_extract_stations_endpoint():
    resp = client.post(
        "/extract-stations",
        files=[("files", ("s.csv", STATION_CSV, "text/csv"))],
        data={"mapping": json.dumps(STATION_MAPPING)},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["stations"]) == 2
    assert len(body["measurements"]) == 2


def test_extract_stations_invalid_mapping_returns_400():
    bad_mapping = {"s.csv": {"station": "STATION_ID", "lat": "LATITUDE"}}  # no LONGITUDE
    resp = client.post(
        "/extract-stations",
        files=[("files", ("s.csv", STATION_CSV, "text/csv"))],
        data={"mapping": json.dumps(bad_mapping)},
    )
    assert resp.status_code == 400


def test_extract_stations_endpoint_applies_renames():
    renames = {"s.csv": {"pm25": "aqi"}}
    resp = client.post(
        "/extract-stations",
        files=[("files", ("s.csv", STATION_CSV, "text/csv"))],
        data={"mapping": json.dumps(STATION_MAPPING), "renames": json.dumps(renames)},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert {m["measurementType"] for m in body["measurements"]} == {"aqi"}
