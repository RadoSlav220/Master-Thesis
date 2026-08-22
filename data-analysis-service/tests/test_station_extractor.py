import pytest

from app.services import station_extractor
from app.services.station_extractor import InvalidMappingError

# Two stations, each with two readings over time.
STATIONS_CSV = (
    b"station,lat,lon,ts,pm25,no2\n"
    b"S1,42.69,23.32,2024-01-01T00:00:00Z,12,30\n"
    b"S1,42.69,23.32,2024-01-01T01:00:00Z,15,28\n"
    b"S2,42.70,23.33,2024-01-01T00:00:00Z,9,20\n"
)

MAPPING = {
    "stations.csv": {
        "station": "STATION_ID",
        "lat": "LATITUDE",
        "lon": "LONGITUDE",
        "ts": "TIMESTAMP",
        "pm25": "MEASUREMENT",
        "no2": "MEASUREMENT",
    }
}


def test_extract_dedups_stations_and_expands_long_format():
    result = station_extractor.extract([("stations.csv", STATIONS_CSV)], MAPPING)

    assert {s.stationExternalId for s in result.stations} == {"S1", "S2"}
    s1 = next(s for s in result.stations if s.stationExternalId == "S1")
    assert s1.latitude == 42.69
    assert s1.longitude == 23.32

    # 3 rows x 2 measurement columns = 6 long-format rows.
    assert len(result.measurements) == 6
    types = {m.measurementType for m in result.measurements}
    assert types == {"pm25", "no2"}
    a_reading = next(m for m in result.measurements if m.measurementType == "pm25")
    assert a_reading.valueNumeric == 12.0
    assert a_reading.timestamp is not None


def test_station_attribute_collected():
    csv = b"station,lat,lon,name\nS1,1.0,2.0,Center\n"
    mapping = {
        "s.csv": {
            "station": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
            "name": "STATION_ATTRIBUTE",
        }
    }
    result = station_extractor.extract([("s.csv", csv)], mapping)
    assert result.stations[0].attributes == {"name": "Center"}
    assert result.measurements == []


def test_missing_required_role_raises():
    csv = b"station,lon,ts,pm25\nS1,23.3,2024-01-01T00:00:00Z,10\n"
    mapping = {
        "s.csv": {
            "station": "STATION_ID",
            "lon": "LONGITUDE",
            "ts": "TIMESTAMP",
            "pm25": "MEASUREMENT",
        }
    }
    with pytest.raises(InvalidMappingError):
        station_extractor.extract([("s.csv", csv)], mapping)


def test_measurement_without_timestamp_raises():
    csv = b"station,lat,lon,pm25\nS1,1.0,2.0,10\n"
    mapping = {
        "s.csv": {
            "station": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
            "pm25": "MEASUREMENT",
        }
    }
    with pytest.raises(InvalidMappingError):
        station_extractor.extract([("s.csv", csv)], mapping)


def test_duplicate_required_role_raises():
    csv = b"station,alt_id,lat,lon\nS1,X,1.0,2.0\n"
    mapping = {
        "s.csv": {
            "station": "STATION_ID",
            "alt_id": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
        }
    }
    with pytest.raises(InvalidMappingError):
        station_extractor.extract([("s.csv", csv)], mapping)


def test_unparseable_timestamp_becomes_null():
    csv = b"station,lat,lon,ts,pm25\nS1,1.0,2.0,not-a-date,10\n"
    mapping = {
        "s.csv": {
            "station": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
            "ts": "TIMESTAMP",
            "pm25": "MEASUREMENT",
        }
    }
    result = station_extractor.extract([("s.csv", csv)], mapping)
    assert result.measurements[0].timestamp is None


def test_conflicting_coordinates_warns_and_keeps_first():
    csv = (
        b"station,lat,lon,ts,pm25\n"
        b"S1,1.0,2.0,2024-01-01T00:00:00Z,10\n"
        b"S1,9.0,9.0,2024-01-01T01:00:00Z,11\n"
    )
    mapping = {
        "s.csv": {
            "station": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
            "ts": "TIMESTAMP",
            "pm25": "MEASUREMENT",
        }
    }
    result = station_extractor.extract([("s.csv", csv)], mapping)
    station = result.stations[0]
    assert station.latitude == 1.0
    assert station.longitude == 2.0
    assert result.warnings


def test_multi_file_merges_stations():
    file_a = b"station,lat,lon,ts,pm25\nS1,1.0,2.0,2024-01-01T00:00:00Z,10\n"
    file_b = b"station,lat,lon,ts,no2\nS1,1.0,2.0,2024-01-01T01:00:00Z,30\n"
    mapping = {
        "a.csv": {
            "station": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
            "ts": "TIMESTAMP",
            "pm25": "MEASUREMENT",
        },
        "b.csv": {
            "station": "STATION_ID",
            "lat": "LATITUDE",
            "lon": "LONGITUDE",
            "ts": "TIMESTAMP",
            "no2": "MEASUREMENT",
        },
    }
    result = station_extractor.extract([("a.csv", file_a), ("b.csv", file_b)], mapping)
    assert len(result.stations) == 1
    assert len(result.measurements) == 2


def test_no_mapping_for_file_raises():
    with pytest.raises(InvalidMappingError):
        station_extractor.extract([("s.csv", b"station,lat,lon\nS1,1,2\n")], {})
