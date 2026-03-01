

```json
{
  "type": "MESSAGE_TYPE",
  "data": {  }
}
```

1. REQUEST_LATEST_BLOCK


```json
{
  "type": "REQUEST_LATEST_BLOCK",
  "data": null
}
```

2. LATEST_BLOCK_RESPONSE


```json
{
  "type": "LATEST_BLOCK_RESPONSE",
  "data": {
    "block": {
      "index": 5,
      "timestamp": "2025-08-06T14:30:00",
      "prevHash": "00000",
      "hash": "00000",
      "nonce": 48192,
      "dataString": "data",
      "analysisResult": "result",
      "imageString": "base64"
    },
    "chainLength": 6
  }
}
```

3. REQUEST_RECENT_BLOCKS


```json
{
  "type": "REQUEST_RECENT_BLOCKS",
  "data": {}
}
```

4. RECENT_BLOCKS_RESPONSE


```json
{
  "type": "RECENT_BLOCKS_RESPONSE",
  "data": {
    "blocks": [
      {
        "index": 950,
        "timestamp": "2025-08-06T14:20:00",
        "prevHash": "00000",
        "hash": "00000",
        "nonce": 12345,
        "dataString": "data",
        "analysisResult": "result",
        "imageString": "base64"
      },
      {
        "index": 951,
        "timestamp": "2025-08-06T14:21:00",
        "prevHash": "00000",
        "hash": "00000",
        "nonce": 67890,
        "dataString": "data",
        "analysisResult": "result",
        "imageString": "base64"
      }
    ],
    "totalChainLength": 1000,
    "fromIndex": 950,
    "toIndex": 999
  }
}
```

5. NEW_BLOCK_REQUEST


```json
{
  "type": "NEW_BLOCK_REQUEST",
  "data": {
    "index": 6,
    "timestamp": "2025-08-06T14:31:00",
    "prevHash": "00000",
    "hash": "00000",
    "nonce": 89234,
    "dataString": "data",
    "analysisResult": "result",
    "imageString": "base64"
  }
}
```

6. BLOCK_ACCEPTED

```json
{
  "type": "BLOCK_ACCEPTED",
  "data": {
    "blockIndex": 6,
    "blockHash": "00000",
    "message": "accepted",
    "reward": 50.0,
    "workerId": "w1"
  }
}
```

7. ERROR_INVALID_BLOCK


```json
{
  "type": "ERROR_INVALID_BLOCK",
  "data": {
    "reason": "Invalid hash",
    "blockIndex": 6,
    "details": "bad hash"
  }
}
```


