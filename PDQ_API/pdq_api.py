#! flask/bin/python

from flask import Flask, request, jsonify, abort, make_response, url_for
from flask_restful import Api, Resource, reqparse, fields, marshal
from flask_httpauth import HTTPBasicAuth
import pyodbc
import json
import config


app = Flask(__name__)
api = Api(app)
# auth = HTTPBasicAuth()

# 
class AzureSQLDatabase(object):
    connection = None
    cursor = None

    def __init__(self):
        self.connection = pyodbc.connect(config.CONN_STRING)
        self.cursor = self.connection.cursor()
    
    def query2(self, query, params):
        return self.cursor.execute(query, params)

    def query(self, query):
        return self.cursor.execute(query)

    def commit(self):
        return self.connection.commit()

    def __del__(self):
        self.connection.close()


# @auth.get_password
# def get_password_and_key(username):
#     """ Simple text-based authentication """
#     if username == '<user-name>':
#         api_key = '<api-key>'
#         return api_key
#     else:
#         return None


# @auth.error_handler
# def unauthorized():
#     """
#     Return a 403 instead of a 401 to prevent browsers from displaying
#     the default auth dialog
#     :param:
#     :return: unauthorized message
#     """
#     return make_response(jsonify({'message': 'Unauthorized Access'}), 403)


truckopcoors = {
    'TruckId': fields.Integer,
    'Latitude': fields.Float,
    'Longitude': fields.Float
}

truckstartcoors = {
    'TruckId': fields.Integer,
    'Latitude': fields.Float,
    'Longitude': fields.Float
}

trucks = {
    'TruckId': fields.Integer,
    'TruckLicensePlate': fields.String,
    'InventoryId': fields.Integer
}

zones = {
    'ZoneId': fields.Integer,
    'StreetCount': fields.Integer,
    'TruckCount': fields.Integer,
    'PopulationDensity': fields.Integer
}

### START of TruckOperationalCoordinates table requests

class TruckOpCoorListAPI(Resource):
    """
    API Resource for listing all operational coordinates from the database.
    Provides the endpoint for creating new operational coordinates
    :param: none
    :type a json object
    :return truckop, status_code
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('TruckId', type=int, required=False,
                                   help='The API URL\'s ID of the truck.')
        self.reqparse.add_argument('Latitude', type=float, required=False,
                                   help='The latitude value of the truck location')
        self.reqparse.add_argument('Longitude', type=float, required=True,
                                   help='The longtitude value of the truck location')

        super(TruckOpCoorListAPI, self).__init__()

    def get(self):
        try:
            sql = "SELECT * FROM [dbo].[TruckOperationalCoordinates]"
            conn = AzureSQLDatabase()
            cursor = conn.query(sql)
            columns = [column[0] for column in cursor.description]
            truckops = []
            for row in cursor.fetchall():
                truckops.append(dict(zip(columns, row)))

            return {
                'truckops': marshal(truckops, truckopcoors)
            }

        except Exception as e:
            return {'error': str(e)}

    def post(self):
        try:
            args = self.reqparse.parse_args()
            data = request.get_json()

            truckopcoor = {
                'TruckId': data['TruckId'],
                'Latitude': data['Latitude'],
                'Longitude': data['Longitude']
            }

            conn = AzureSQLDatabase()
            conn.query2("INSERT INTO [dbo].[TruckOperationalCoordinates]([TruckId], [Latitude], [Longitude]) \
                 VALUES (?, ?, ?)",
                       [truckopcoor['TruckId'], truckopcoor['Latitude'], truckopcoor['Longitude']])

            conn.commit()

            return {
                'truckopcoor': truckopcoor
            }, 201

        except Exception as e:
            return {'error': str(e)}


class TruckOpCoorAPI(Resource):
    """
    API Resource for retrieving, modifying, updating and deleting a single
    truck operational coordinate, by ID.
    :param: TruckId
    :return: Truck operational details by ID.
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('TruckId', type=int, required=False,
                                   help='The API URL\'s ID of the truck.')
        self.reqparse.add_argument('Latitude', type=float, required=False,
                                   help='The latitude value of the truck location', location='args')
        self.reqparse.add_argument('Longitude', type=float, required=True,
                                   help='The longtitude value of the truck location', location='args')

        super(TruckOpCoorAPI, self).__init__()

    def get(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "SELECT [TruckId], [Latitude], [Longitude] FROM [dbo].[TruckOperationalCoordinates] WHERE [TruckId] = ?"
            cursor = conn.query2(sql, params)
            columns = [column[0] for column in cursor.description]
            truckop = []
            for row in cursor.fetchall():
                truckop.append(dict(zip(columns, row)))

            return {
                'truckop': marshal(truckop, truckopcoors)
            }, 200

        except Exception as e:
            return {'error': str(e)}

    def put(self, id):
        try:
            conn = AzureSQLDatabase()
            data = request.get_json()
            params = (data['Latitude'], data['Longitude'], id)
            conn.query2("UPDATE [dbo].[TruckOperationalCoordinates] SET [Latitude] = ?, [Longitude] = ? WHERE [TruckId] = ?", params)

            conn.commit()

            return {
                'truckop': data
            }, 204

        except Exception as e:
            return {'error': str(e)}

    def delete(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "DELETE FROM [dbo].[TruckOperationalCoordinates] WHERE [TruckId] = ?"
            cursor = conn.query2(sql, params)
            conn.commit()

            return {
                'result': True
            }, 204

        except Exception as e:
            return {'error': str(e)}

### END of TruckOperationalCoordinates Table

### START of TruckStartingCoordinates Table requests

class TruckStartCoorListAPI(Resource):
    """
    API Resource for listing all starting coordinates from the database.
    Provides the endpoint for creating new starting coordinates for additional trucks
    :param: none
    :type a json object
    :return truckstarts, status_code
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('TruckId', type=int, required=False,
                                   help='The API URL\'s ID of the truck.')
        self.reqparse.add_argument('Latitude', type=float, required=False,
                                   help='The latitude value of the truck start location')
        self.reqparse.add_argument('Longitude', type=float, required=True,
                                   help='The longtitude value of the truck start location')

        super(TruckStartCoorListAPI, self).__init__()

    def get(self):
        try:
            sql = "SELECT * FROM [dbo].[TruckStartingCoordinates]"
            conn = AzureSQLDatabase()
            cursor = conn.query(sql)
            columns = [column[0] for column in cursor.description]
            truckstarts = []
            for row in cursor.fetchall():
                truckstarts.append(dict(zip(columns, row)))

            return {
                'truckstarts': marshal(truckstarts, truckstartcoors)
            }

        except Exception as e:
            return {'error': str(e)}

    def post(self):
        try:
            args = self.reqparse.parse_args()
            data = request.get_json()

            truckstartcoor = {
                'TruckId': data['TruckId'],
                'Latitude': data['Latitude'],
                'Longitude': data['Longitude']
            }

            conn = AzureSQLDatabase()
            conn.query2("INSERT INTO [dbo].[TruckStartingCoordinates]([TruckId], [Latitude], [Longitude]) \
                 VALUES (?, ?, ?)",
                       [truckstartcoor['TruckId'], truckstartcoor['Latitude'], truckstartcoor['Longitude']])

            conn.commit()

            return {
                'truckstartcoor': truckstartcoor
            }, 201

        except Exception as e:
            return {'error': str(e)}


class TruckStartCoorAPI(Resource):
    """
    API Resource for retrieving, modifying, updating and deleting a single
    truck starting coordinate, by ID.
    :param: TruckId
    :return: Truck starting details by ID.
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('TruckId', type=int, required=False,
                                   help='The API URL\'s ID of the truck.')
        self.reqparse.add_argument('Latitude', type=float, required=False,
                                   help='The latitude value of the truck location', location='args')
        self.reqparse.add_argument('Longitude', type=float, required=True,
                                   help='The longtitude value of the truck location', location='args')

        super(TruckStartCoorAPI, self).__init__()

    def get(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "SELECT [TruckId], [Latitude], [Longitude] FROM [dbo].[TruckStartingCoordinates] WHERE [TruckId] = ?"
            cursor = conn.query2(sql, params)
            columns = [column[0] for column in cursor.description]
            truckstart = []
            for row in cursor.fetchall():
                truckstart.append(dict(zip(columns, row)))

            return {
                'truckstart': marshal(truckstart, truckstartcoors)
            }, 200

        except Exception as e:
            return {'error': str(e)}

    def put(self, id):
        try:
            conn = AzureSQLDatabase()
            data = request.get_json()
            params = (data['Latitude'], data['Longitude'], id)
            conn.query2("UPDATE [dbo].[TruckStartingCoordinates] SET [Latitude] = ?, [Longitude] = ? WHERE [TruckId] = ?", params)

            conn.commit()

            return {
                'truckstart': data
            }, 204

        except Exception as e:
            return {'error': str(e)}

    def delete(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "DELETE FROM [dbo].[TruckStartingCoordinates] WHERE [TruckId] = ?"
            cursor = conn.query2(sql, params)
            conn.commit()

            return {
                'result': True
            }, 204

        except Exception as e:
            return {'error': str(e)}

### END of TruckStartingCoordinates Table

### START of Trucks Table requests

class TrucksListAPI(Resource):
    """
    API Resource for listing all trucks from the database.
    Provides the endpoint for adding information for additional trucks
    :param: none
    :type a json object
    :return truckslist, status_code
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('TruckId', type=int, required=True,
                                   help='The API URL\'s ID of the truck.')
        self.reqparse.add_argument('TruckLicensePlate', type=str, required=True,
                                   help='The license plate value of the truck')
        self.reqparse.add_argument('InventoryId', type=int, required=True,
                                   help='The inventory Id value of the truck')

        super(TrucksListAPI, self).__init__()

    def get(self):
        try:
            sql = "SELECT * FROM [dbo].[Trucks]"
            conn = AzureSQLDatabase()
            cursor = conn.query(sql)
            columns = [column[0] for column in cursor.description]
            truckslist = []
            for row in cursor.fetchall():
                truckslist.append(dict(zip(columns, row)))

            return {
                'truckslist': marshal(truckslist, trucks)
            }

        except Exception as e:
            return {'error': str(e)}

    def post(self):
        try:
            args = self.reqparse.parse_args()
            data = request.get_json()

            truck = {
                'TruckId': data['TruckId'],
                'TruckLicensePlate': data['TruckLicensePlate'],
                'InventoryId': data['InventoryId']
            }

            conn = AzureSQLDatabase()
            conn.query2("INSERT INTO [dbo].[Trucks]([TruckId], [TruckLicensePlate], [InventoryId])\
                 VALUES (?, ?, ?)",
                       [truck['TruckId'], truck['TruckLicensePlate'], truck['InventoryId']])

            conn.commit()

            return {
                'truck': truck
            }, 201

        except Exception as e:
            return {'error': str(e)}


class TrucksAPI(Resource):
    """
    API Resource for retrieving, modifying, updating and deleting a single
    truck by ID.
    :param: TruckId
    :return: Truck details by ID.
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('TruckId', type=int, required=False,
                                   help='The API URL\'s ID of the truck.')
        self.reqparse.add_argument('TruckLicensePlate', type=str, required=False,
                                   help='The license plate value of the truck', location='args')
        self.reqparse.add_argument('InventoryId', type=int, required=True,
                                   help='The inventory Id of the truck', location='args')

        super(TrucksAPI, self).__init__()

    def get(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "SELECT [TruckId], [TruckLicensePlate], [InventoryId] FROM [dbo].[Trucks] WHERE [TruckId] = ?"
            cursor = conn.query2(sql, params)
            columns = [column[0] for column in cursor.description]
            truck_item = []
            for row in cursor.fetchall():
                truck_item.append(dict(zip(columns, row)))

            return {
                'truck_item': marshal(truck_item, trucks)
            }, 200

        except Exception as e:
            return {'error': str(e)}

    def put(self, id):
        try:
            conn = AzureSQLDatabase()
            data = request.get_json()
            params = (data['TruckLicensePlate'], data['InventoryId'], id)
            conn.query2("UPDATE [dbo].[Trucks] SET [TruckLicensePlate] = ?, [InventoryId] = ? WHERE [TruckId] = ?", params)
            conn.commit()

            return {
                'truck_item': data
            }, 204

        except Exception as e:
            return {'error': str(e)}

    def delete(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "DELETE FROM [dbo].[Trucks] WHERE [TruckId] = ?"
            cursor = conn.query2(sql, params)
            conn.commit()

            return {
                'result': True
            }, 204

        except Exception as e:
            return {'error': str(e)}

### END of Trucks Table

### START of Zones Table requests

class ZonesListAPI(Resource):
    """
    API Resource for listing all zoness from the database.
    Provides the endpoint for adding information for additional zones
    :param: none
    :type a json object
    :return zoneslist, status_code
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('ZoneId', type=int, required=True,
                                   help='The API URL\'s ID of the zone.')
        self.reqparse.add_argument('StreetCount', type=int, required=True,
                                   help='The number of streets in a zone')
        self.reqparse.add_argument('TruckCount', type=int, required=True,
                                   help='The number of trucks in a zone')
        self.reqparse.add_argument('PopulationDensity', type=int, required=True,
                                   help='The population density of a zone')

        super(ZonesListAPI, self).__init__()

    def get(self):
        try:
            sql = "SELECT * FROM [dbo].[Zones]"
            conn = AzureSQLDatabase()
            cursor = conn.query(sql)
            columns = [column[0] for column in cursor.description]
            zoneslist = []
            for row in cursor.fetchall():
                zoneslist.append(dict(zip(columns, row)))

            return {
                'zoneslist': marshal(zoneslist, zones)
            }

        except Exception as e:
            return {'error': str(e)}

    def post(self):
        try:
            args = self.reqparse.parse_args()
            data = request.get_json()

            zone = {
                'ZoneId': data['ZoneId'],
                'StreetCount': data['StreetCount'],
                'TruckCount': data['TruckCount'],
                'PopulationDensity': data['PopulationDensity']
            }

            conn = AzureSQLDatabase()
            conn.query2("INSERT INTO [dbo].[Zones]([ZoneId], [StreetCount], [TruckCount], [PopulationDensity])\
                 VALUES (?, ?, ?, ?)",
                       [zone['ZoneId'], zone['StreetCount'], zone['TruckCount'], zone['PopulationDensity']])

            conn.commit()

            return {
                'zone': zone
            }, 201

        except Exception as e:
            return {'error': str(e)}


class ZonesAPI(Resource):
    """
    API Resource for retrieving, modifying, updating and deleting a single
    zone by ID.
    :param: ZoneId
    :return: Zone details by ID.
    """
    # decorators = [auth.login_required]

    def __init__(self):
        self.reqparse = reqparse.RequestParser()
        self.reqparse.add_argument('ZoneId', type=int, required=False,
                                   help='The API URL\'s ID of the zone.')
        self.reqparse.add_argument('StreetCount', type=int, required=False,
                                   help='The number of streets in a zone', location='args')
        self.reqparse.add_argument('TruckCount', type=int, required=True,
                                   help='The number of trucks in a zone', location='args')
        self.reqparse.add_argument('PopulationDensity', type=int, required=True,
                                   help='The number of trucks in a zone', location='args')

        super(ZonesAPI, self).__init__()

    def get(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "SELECT [ZoneId], [StreetCount], [TruckCount], [PopulationDensity] FROM [dbo].[Zones] WHERE [ZoneId] = ?"
            cursor = conn.query2(sql, params)
            columns = [column[0] for column in cursor.description]
            zone_item = []
            for row in cursor.fetchall():
                zone_item.append(dict(zip(columns, row)))

            return {
                'zone_item': marshal(zone_item, zones)
            }, 200

        except Exception as e:
            return {'error': str(e)}

    def put(self, id):
        try:
            conn = AzureSQLDatabase()
            data = request.get_json()
            params = (data['StreetCount'], data['TruckCount'], data['PopulationDensity'], id)
            conn.query2("UPDATE [dbo].[Zones] SET [StreetCount] = ?, [TruckCount] = ?, [PopulationDensity] = ? WHERE [ZoneId] = ?", params)
            conn.commit()

            return {
                'zone_item': data
            }, 204

        except Exception as e:
            return {'error': str(e)}

    def delete(self, id):
        try:
            conn = AzureSQLDatabase()
            params = id
            sql = "DELETE FROM [dbo].[Zones] WHERE [ZoneId] = ?"
            cursor = conn.query2(sql, params)
            conn.commit()

            return {
                'result': True
            }, 204

        except Exception as e:
            return {'error': str(e)}

### END of Zones Table


# register the API resources and define endpoints
api.add_resource(TruckOpCoorListAPI, '/pdq/truckoperationalcoordinates/', endpoint='truckops')
api.add_resource(TruckOpCoorAPI, '/pdq/truckoperationalcoordinates/<int:id>', endpoint='truckop')
api.add_resource(TruckStartCoorListAPI, '/pdq/truckstartingcoordinates/', endpoint='truckstarts')
api.add_resource(TruckStartCoorAPI, '/pdq/truckstartingcoordinates/<int:id>', endpoint='truckstart')
api.add_resource(TrucksListAPI, '/pdq/trucks/', endpoint='trucks_list')
api.add_resource(TrucksAPI, '/pdq/trucks/<int:id>', endpoint='truck_item')
api.add_resource(ZonesListAPI, '/pdq/zones/', endpoint='zones_list')
api.add_resource(ZonesAPI, '/pdq/zone/<int:id>', endpoint='zone_item')
# api.add_resource(Energy, '/DPM')


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)