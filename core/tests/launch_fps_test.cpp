#include "borealis/host.h"

#include <cstdint>
#include <gtest/gtest.h>

TEST(LaunchFpsGfe, KeepsRatesUpTo60) {
  EXPECT_EQ(borealis_launch_fps(1, 30, 30), 30);
  EXPECT_EQ(borealis_launch_fps(1, 59, 59), 59);
  EXPECT_EQ(borealis_launch_fps(1, 60, 60), 60);
}

TEST(LaunchFpsGfe, ZeroesRatesAbove60) {
  EXPECT_EQ(borealis_launch_fps(1, 61, 61), 0);
  EXPECT_EQ(borealis_launch_fps(1, 120, 120), 0);
  EXPECT_EQ(borealis_launch_fps(1, INT32_MAX, 30), 0);
}

TEST(LaunchFpsGfe, IgnoresStreamFps) {
  EXPECT_EQ(borealis_launch_fps(1, 60, 59), 60);
  EXPECT_EQ(borealis_launch_fps(1, 120, 59), 0);
  EXPECT_EQ(borealis_launch_fps(1, 30, 144), 30);
}

TEST(LaunchFpsGfe, AnyNonZeroFlagMeansGfe) {
  EXPECT_EQ(borealis_launch_fps(2, 120, 59), 0);
  EXPECT_EQ(borealis_launch_fps(-1, 120, 59), 0);
}

TEST(LaunchFpsGfe, NonPositiveLaunchFpsYieldsZero) {
  EXPECT_EQ(borealis_launch_fps(1, 0, 60), 0);
  EXPECT_EQ(borealis_launch_fps(1, -30, 60), 0);
  EXPECT_EQ(borealis_launch_fps(1, INT32_MIN, 60), 0);
}

TEST(LaunchFpsOtherHosts, UsesStreamFps) {
  EXPECT_EQ(borealis_launch_fps(0, 60, 59), 59);
  EXPECT_EQ(borealis_launch_fps(0, 120, 120), 120);
  EXPECT_EQ(borealis_launch_fps(0, 144, 90), 90);
  EXPECT_EQ(borealis_launch_fps(0, 30, 60), 60);
  EXPECT_EQ(borealis_launch_fps(0, 60, INT32_MAX), INT32_MAX);
}

TEST(LaunchFpsOtherHosts, NonPositiveStreamFpsFallsBackToTheGfeRule) {
  EXPECT_EQ(borealis_launch_fps(0, 60, 0), 60);
  EXPECT_EQ(borealis_launch_fps(0, 120, 0), 0);
  EXPECT_EQ(borealis_launch_fps(0, 90, -1), 0);
  EXPECT_EQ(borealis_launch_fps(0, 45, INT32_MIN), 45);
}

TEST(LaunchFpsOtherHosts, ResultIsNeverNegative) {
  EXPECT_EQ(borealis_launch_fps(0, -30, 0), 0);
  EXPECT_EQ(borealis_launch_fps(0, INT32_MIN, -5), 0);
  EXPECT_EQ(borealis_launch_fps(0, -30, 90), 90);
}
