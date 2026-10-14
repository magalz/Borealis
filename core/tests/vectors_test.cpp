// Replays the golden vectors in tests/fixtures/vectors/*.tsv (format documented in
// tests/fixtures/README.md) and the LuminalShine 26.09.1 /serverinfo fixture.
#include "borealis/host.h"

#include <cstddef>
#include <cstdint>
#include <fstream>
#include <gtest/gtest.h>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace {

  const std::string kFixturesDir = BOREALIS_CORE_FIXTURES_DIR;

  using Row = std::vector<std::string>;

  struct Table {
    std::vector<std::string> header;
    std::vector<Row> rows;
  };

  std::string read_file(const std::string &path) {
    std::ifstream in(path, std::ios::binary);
    EXPECT_TRUE(in.is_open()) << "cannot open " << path;
    std::ostringstream text;
    text << in.rdbuf();
    return text.str();
  }

  // Splits on tabs only: no trimming, so leading and trailing spaces are data.
  Row split_tabs(const std::string &line) {
    Row fields;
    std::size_t start = 0;
    while (true) {
      const std::size_t tab = line.find('\t', start);
      if (tab == std::string::npos) {
        fields.push_back(line.substr(start));
        return fields;
      }
      fields.push_back(line.substr(start, tab - start));
      start = tab + 1;
    }
  }

  // Skips blank lines and `#` comments; the first remaining line is the header.
  Table load_table(const std::string &name) {
    std::istringstream lines(read_file(kFixturesDir + "/vectors/" + name));
    Table table;
    std::string line;
    while (std::getline(lines, line)) {
      if (!line.empty() && line.back() == '\r') {
        line.pop_back();
      }
      if (line.empty() || line.front() == '#') {
        continue;
      }
      Row fields = split_tabs(line);
      if (table.header.empty()) {
        table.header = std::move(fields);
      } else {
        EXPECT_EQ(fields.size(), table.header.size()) << name << ": " << line;
        if (fields.size() == table.header.size()) {
          table.rows.push_back(std::move(fields));
        }
      }
    }
    EXPECT_FALSE(table.header.empty()) << name;
    return table;
  }

  int32_t to_int(const std::string &text) {
    return static_cast<int32_t>(std::stoll(text, nullptr, 0));
  }

  uint32_t to_uint(const std::string &text) {
    return static_cast<uint32_t>(std::stoull(text, nullptr, 0));
  }

  // `<null>` is a NULL pointer with length 0, `<empty>` a non-NULL empty string.
  struct Text {
    std::string value;
    bool is_null = false;

    [[nodiscard]] const char *data() const {
      return is_null ? nullptr : value.c_str();
    }
  };

  Text to_text(const std::string &field) {
    if (field == "<null>") {
      return {"", true};
    }
    if (field == "<empty>") {
      return {"", false};
    }
    return {field, false};
  }

  int32_t family_value(const std::string &name) {
    if (name == "UNKNOWN") {
      return BOREALIS_HOST_FAMILY_UNKNOWN;
    }
    if (name == "GFE") {
      return BOREALIS_HOST_FAMILY_GFE;
    }
    if (name == "SUNSHINE") {
      return BOREALIS_HOST_FAMILY_SUNSHINE;
    }
    ADD_FAILURE() << "unknown family " << name;
    return -1;
  }

  int32_t verb_value(const std::string &name) {
    if (name == "LAUNCH") {
      return BOREALIS_LAUNCH_VERB_LAUNCH;
    }
    if (name == "RESUME") {
      return BOREALIS_LAUNCH_VERB_RESUME;
    }
    ADD_FAILURE() << "unknown verb " << name;
    return -1;
  }

  int32_t action_value(const std::string &name) {
    if (name == "FAIL") {
      return BOREALIS_LAUNCH_ACTION_FAIL;
    }
    if (name == "RETRY") {
      return BOREALIS_LAUNCH_ACTION_RETRY;
    }
    if (name == "FALLBACK_TO_LAUNCH") {
      return BOREALIS_LAUNCH_ACTION_FALLBACK_TO_LAUNCH;
    }
    ADD_FAILURE() << "unknown action " << name;
    return -1;
  }

  int32_t category_value(const std::string &name) {
    if (name == "PERMANENT") {
      return BOREALIS_LAUNCH_CATEGORY_PERMANENT;
    }
    if (name == "PENDING") {
      return BOREALIS_LAUNCH_CATEGORY_PENDING;
    }
    if (name == "RESTORING_DISPLAY") {
      return BOREALIS_LAUNCH_CATEGORY_RESTORING_DISPLAY;
    }
    if (name == "GPU_NOT_READY") {
      return BOREALIS_LAUNCH_CATEGORY_GPU_NOT_READY;
    }
    if (name == "CAPTURE_OR_DISPLAY") {
      return BOREALIS_LAUNCH_CATEGORY_CAPTURE_OR_DISPLAY;
    }
    if (name == "NO_RUNNING_APP") {
      return BOREALIS_LAUNCH_CATEGORY_NO_RUNNING_APP;
    }
    ADD_FAILURE() << "unknown category " << name;
    return -1;
  }

  std::string xml_text(const std::string &xml, const std::string &tag) {
    const std::string open = "<" + tag + ">";
    const std::size_t begin = xml.find(open);
    const std::size_t end = xml.find("</" + tag + ">");
    if (begin == std::string::npos || end == std::string::npos) {
      ADD_FAILURE() << "tag " << tag << " not found";
      return "";
    }
    return xml.substr(begin + open.size(), end - begin - open.size());
  }

}  // namespace

TEST(GoldenVectors, HostCaps) {
  const Table table = load_table("host_caps.tsv");
  ASSERT_FALSE(table.rows.empty());
  for (const Row &row : table.rows) {
    SCOPED_TRACE("appversion [" + row[0] + "] mask " + row[1]);
    const Text appversion = to_text(row[0]);
    borealis_host_caps caps {};
    ASSERT_EQ(
      borealis_host_parse_caps(appversion.data(), appversion.value.size(), to_uint(row[1]), &caps),
      BOREALIS_OK
    );
    EXPECT_EQ(caps.family, family_value(row[2]));
    for (std::size_t i = 0; i < 4; ++i) {
      EXPECT_EQ(caps.version[i], to_int(row[3 + i])) << "version[" << i << "]";
    }
    EXPECT_EQ(caps.codec_flags, to_uint(row[7]));
    EXPECT_EQ(caps.pyrowave_profiles, to_uint(row[8]));
  }
}

TEST(GoldenVectors, LaunchDecide) {
  const Table table = load_table("launch_decide.tsv");
  ASSERT_FALSE(table.rows.empty());
  for (const Row &row : table.rows) {
    SCOPED_TRACE(row[0] + " " + row[1] + " [" + row[2] + "] attempts " + row[3]);
    const Text message = to_text(row[2]);
    borealis_launch_decision out {};
    ASSERT_EQ(
      borealis_launch_decide(
        verb_value(row[0]),
        to_int(row[1]),
        message.data(),
        message.value.size(),
        to_uint(row[3]),
        &out
      ),
      BOREALIS_OK
    );
    EXPECT_EQ(out.action, action_value(row[4]));
    EXPECT_EQ(out.delay_ms, to_int(row[5]));
    EXPECT_EQ(out.category, category_value(row[6]));
    EXPECT_EQ(out.max_attempts, to_int(row[7]));
  }
}

TEST(GoldenVectors, LaunchFps) {
  const Table table = load_table("launch_fps.tsv");
  ASSERT_FALSE(table.rows.empty());
  for (const Row &row : table.rows) {
    SCOPED_TRACE("is_gfe " + row[0] + " launch " + row[1] + " stream " + row[2]);
    EXPECT_EQ(borealis_launch_fps(to_int(row[0]), to_int(row[1]), to_int(row[2])), to_int(row[3]));
  }
}

TEST(Luminalshine26_09_1, ServerinfoFixtureParsesAsSunshine) {
  const std::string xml = read_file(kFixturesDir + "/luminalshine/26.09.1/serverinfo-http.xml");
  const std::string appversion = xml_text(xml, "appversion");
  const std::string mask = xml_text(xml, "ServerCodecModeSupport");
  ASSERT_EQ(appversion, "7.1.431.-1");
  ASSERT_EQ(to_uint(mask), 0x001C0301U);

  borealis_host_caps caps {};
  ASSERT_EQ(borealis_host_parse_caps(appversion.data(), appversion.size(), to_uint(mask), &caps), BOREALIS_OK);
  EXPECT_EQ(caps.family, BOREALIS_HOST_FAMILY_SUNSHINE);
  EXPECT_EQ(caps.version[0], 7);
  EXPECT_EQ(caps.version[1], 1);
  EXPECT_EQ(caps.version[2], 431);
  EXPECT_EQ(caps.version[3], -1);
  EXPECT_EQ(caps.codec_flags, BOREALIS_CODEC_HEVC | BOREALIS_CODEC_HEVC_MAIN10 | BOREALIS_CODEC_YUV444);
  EXPECT_EQ(caps.pyrowave_profiles, 0U);
}
