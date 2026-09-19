"""가이드의 구조화 출력(JSON)이 흘러오는 동안 답변 문장만 뽑아내는 도구입니다.

모델은 `{"intent": ..., "answer": "...", ...}` 한 덩어리를 조금씩 내보냅니다. 화면에 글자를 바로 보여 주려면
그 JSON 조각에서 `answer` 값만 골라내야 합니다. 여기서는 따옴표·이스케이프를 직접 따라가며 확정된 글자만 내보내고,
아직 끝나지 않은 이스케이프(`\\u12`)는 완성될 때까지 들고 있습니다. 파싱이 어긋나면 글자를 지어내지 않고 그냥 멈춥니다.
"""

ANSWER_KEY = "answer"
_ESCAPES = {'"': '"', "\\": "\\", "/": "/", "b": "\b", "f": "\f", "n": "\n", "r": "\r", "t": "\t"}
_HIGH_SURROGATE = range(0xD800, 0xDC00)
_LOW_SURROGATE = range(0xDC00, 0xE000)


class AnswerTextStream:
    """JSON 조각을 넣으면 그 안에서 새로 확정된 `answer` 글자를 돌려줍니다. 값이 끝나면 더 읽지 않습니다."""

    def __init__(self) -> None:
        self._depth = 0
        self._in_string = False
        self._escape = False
        self._unicode: str | None = None
        self._high: int | None = None
        self._string: list[str] = []
        self._last_key: str | None = None
        self._expect_value = False
        self._capturing = False
        self._finished = False

    @property
    def finished(self) -> bool:
        """`answer` 값을 끝까지 읽었거나 값이 없어서 더 볼 것이 없는 상태입니다."""
        return self._finished

    def feed(self, chunk: str) -> str:
        out: list[str] = []
        for character in chunk:
            if self._finished:
                break
            if self._in_string:
                self._read_in_string(character, out)
            else:
                self._read_outside_string(character)
        return "".join(out)

    def _read_in_string(self, character: str, out: list[str]) -> None:
        if self._unicode is not None:
            self._read_unicode(character, out)
            return
        if self._escape:
            self._escape = False
            if character == "u":
                self._unicode = ""
            else:
                self._append(_ESCAPES.get(character, character), out)
            return
        if character == "\\":
            self._escape = True
            return
        if character == '"':
            self._in_string = False
            if self._capturing:
                # 값이 끝났습니다. 뒤에 오는 다른 항목은 읽을 필요가 없습니다.
                self._capturing = False
                self._finished = True
            else:
                self._last_key = "".join(self._string)
            return
        self._append(character, out)

    def _read_unicode(self, character: str, out: list[str]) -> None:
        self._unicode = (self._unicode or "") + character
        if len(self._unicode) < 4:
            return
        digits, self._unicode = self._unicode, None
        try:
            code = int(digits, 16)
        except ValueError:
            # 알 수 없는 이스케이프입니다. 깨진 글자를 만들지 않고 여기서 멈춥니다.
            self._finished = True
            return
        if code in _HIGH_SURROGATE:
            self._high = code
            return
        if code in _LOW_SURROGATE:
            if self._high is None:
                # 짝이 없는 대리 문자는 UTF-8로 보낼 수 없으므로 버립니다.
                return
            code = 0x10000 + ((self._high - 0xD800) << 10) + (code - 0xDC00)
            self._high = None
        else:
            self._high = None
        self._append(chr(code), out)

    def _append(self, text: str, out: list[str]) -> None:
        if self._capturing:
            out.append(text)
        else:
            self._string.append(text)

    def _read_outside_string(self, character: str) -> None:
        if character == '"':
            self._in_string = True
            self._string = []
            if self._expect_value and self._depth == 1 and self._last_key == ANSWER_KEY:
                self._capturing = True
            self._expect_value = False
            return
        if character == ":":
            self._expect_value = True
            return
        if character in "{[":
            self._depth += 1
            self._expect_value = False
            return
        if character in "}]":
            self._depth -= 1
            self._expect_value = False
            return
        if character == ",":
            self._expect_value = False
            return
        if character.isspace():
            return
        # 문자열이 아닌 값입니다. `answer`가 null이면 보여 줄 글자가 없습니다.
        if self._expect_value and self._depth == 1 and self._last_key == ANSWER_KEY:
            self._finished = True
        self._expect_value = False
