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

CSV = b"timestamp,temperature\n1,20\n2,21\n"


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


def test_filter_geojson_endpoint():
    resp = client.post(
        "/filter",
        files={"file": ("d.geojson", GEOJSON, "application/json")},
        data={"columns": "pm25", "limit": 1},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["datasetType"] == "GEOJSON"
    fc = json.loads(body["content"])
    assert len(fc["features"]) == 1
    assert set(fc["features"][0]["properties"].keys()) == {"pm25"}


def test_filter_csv_endpoint():
    resp = client.post(
        "/filter",
        files={"file": ("d.csv", CSV, "text/csv")},
        data={"columns": "timestamp", "limit": 1},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["datasetType"] == "CSV"
    lines = body["content"].strip().splitlines()
    assert lines[0] == "timestamp"
    assert len(lines) == 2  # header + 1 row
