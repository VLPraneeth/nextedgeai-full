import json

from syncari.router import Router
from syncari.synapse.abstract_synapse import Synapse
from syncari.models.request import Response, RequestType
from syncari.models.core import (SynapseInfo,
    AuthMetadata, UIMetadata, AuthType, AuthField)
from syncari.models.schema import DataType

# [START functions_execute]
def execute(request):
    print(request)
    synapse_request = request.data
    router = Router(ReferenceSynapse(synapse_request))
    return router.route().json()

class ReferenceSynapse(Synapse):
    def __print(self, funcname, request):
        self.logger.info(funcname)
        self.logger.info(request)
        print()

    @classmethod
    def __get_mock_response(cls, req_type):
        mock_values = {'name':req_type.name}
        return Response(body=json.dumps(mock_values))

    def synapse_info(self):
        return Response(body=SynapseInfo(
            name='customsynapse',category='crm',
            metadata=UIMetadata(displayName='Custom Synapse'),
            supportedAuthTypes=[AuthMetadata(authType=AuthType.USER_PWD,
                fields=[AuthField(name='userName', label='User Name',dataType=DataType.STRING),
                    AuthField(name='password', label='Password',dataType=DataType.PASSWORD)])],
            configuredFields=[AuthField(name='endpoint', label='Endpoint URL',dataType=DataType.STRING)]).json())

    def init(self):
        return Response(body=self.request.connection.json())

    def refresh_token(self):
        return super().refresh_token()

    def get_access_token(self):
        return super().get_access_token()

    def describe(self):
        self.__print(self.describe.__name__, self.request)
        return self.__get_mock_response(RequestType.DESCRIBE)

    def read(self):
        self.__print(self.read.__name__, self.request)
        return self.__get_mock_response(RequestType.READ)

    def get(self):
        return super().get()

    def create(self):
        return super().create()

    def update(self):
        return super().update()

    def delete(self):
        return super().delete()

    def extract_webhook_identifier(self):
        return super().extract_webhook_identifier()

    def process_webhook(self):
        self.__print(self.process_webhook.__name__, self.request)
        return self.__get_mock_response(RequestType.PROCESS_WEBHOOK)
