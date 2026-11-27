import io
import json
import unittest
from unittest import mock

from peregrine import servers


class ResolveJoinTests(unittest.TestCase):
    def test_srv_is_followed(self):
        data = {"Answer": [{"type": 33, "data": "10 5 25565 tcpshield.example.org."}]}
        with mock.patch("urllib.request.urlopen", return_value=io.BytesIO(json.dumps(data).encode())) as _:
            self.assertEqual(servers.resolve_join("play.example.org"), ("tcpshield.example.org:25565", None))

    def test_port_given_is_left_alone(self):
        self.assertEqual(servers.resolve_join("a.example.org:25570"), ("a.example.org:25570", None))


if __name__ == "__main__":
    unittest.main()
