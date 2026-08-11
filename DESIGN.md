---
version: alpha
name: "Climbers"
description: "Design tokens extracted by DesignAgent from 10 Figma frames (Alternative UI Concept 2 - Live Send)."
colors:
  primary: "#c6ff3d"
  surface: "#f5fafa"
  on-surface: "#7c8a8f"
  accent: "#161b1f"
  accent-3: "#1f262b"
  accent-4: "#0b0e10"
  accent-5: "#ff3d5a"
  neutral-1: "#000000"
  accent-6: "#ffd23d"
  accent-7: "#3da9fc"
typography:
  display:
    fontFamily: "Inter"
    fontSize: 38px
    fontWeight: 800
  display-2:
    fontFamily: "Inter"
    fontSize: 32px
    fontWeight: 700
  headline:
    fontFamily: "Inter"
    fontSize: 30px
    fontWeight: 700
  headline-2:
    fontFamily: "Inter"
    fontSize: 28px
    fontWeight: 800
  headline-3:
    fontFamily: "Inter"
    fontSize: 26px
    fontWeight: 800
  headline-4:
    fontFamily: "Inter"
    fontSize: 24px
    fontWeight: 800
  title:
    fontFamily: "Inter"
    fontSize: 22px
    fontWeight: 700
  title-2:
    fontFamily: "Inter"
    fontSize: 22px
    fontWeight: 800
rounded:
  none: 0px
  sm: 2px
  md: 4px
  lg: 5px
  xl: 10px
---

## Overview

"Climbers" - Alternative UI Concept 2, "Live Send" Energetic Sport Style (with Auth + Gym-Mode Dashboard). 10 Figma frames: OnboardingScreen, LoginScreen, SignupScreen, HomeFeedScreen, ClubDashboardScreen, RouteDetailScreen, ExploreScreen, ProgressScreen, CommunityScreen, ProfileScreen.

## Layout

No auto-layout on the analyzed roots - positions are freeform absolute layout. Spacing tokens reflect gaps/padding observed in nested layers.

## Elevation & Depth

Flat - no drop shadows detected. Use borders and surface contrast for depth.

## Shapes

Corner radii: none 0px, sm 2px, md 4px, lg 5px, xl 10px.

## Do's and Don'ts

- Do use the token values in the frontmatter verbatim; treat them as source of truth.
- Do reference color tokens rather than hardcoding hexes.
- Don't introduce new colors, type sizes, or spacing values outside these scales without reason.
- Designer note: this is a design exploration ("Alternative UI Concept 2") - build as a separate, self-contained package with its own nav host. Do not modify or merge into the existing shipped MemberClubNavHost / main app navigation graph.
