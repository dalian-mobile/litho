/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho;

import static com.facebook.litho.ComponentTree.INVALID_LAYOUT_VERSION;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Preconditions;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.ComponentTree.LayoutStateFuture;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps objects which should only be available for the duration of a LayoutState, to access them in
 * other classes such as ComponentContext during layout state calculation. When the layout
 * calculation finishes, the LayoutState reference is nullified. Using a wrapper instead of passing
 * the instances directly helps with clearing out the reference from all objects that hold on to it,
 * without having to keep track of all these objects to clear out the references.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class LayoutStateContext {

  private final @Nullable ComponentTree mComponentTree;
  private @Nullable LayoutProcessInfo mLayoutProcessInfo;
  private @Nullable TreeState mTreeState;
  private @Nullable LayoutStateFuture mLayoutStateFuture;
  private @Nullable DiffNode mCurrentDiffTree;
  private @Nullable ComponentContext mRootComponentContext;
  private final int mLayoutVersion;

  private @Nullable DiffNode mCurrentNestedTreeDiffNode;
  private boolean mIsReleased = false;

  private @Nullable PerfEvent mPerfEvent;

  private LayoutPhaseMeasuredResultCache mCache;

  private final String mThreadCreatedOn;
  private List<String> mThreadReleasedOn = new LinkedList<>();
  private List<String> mThreadResumedOn = new LinkedList<>();

  private final RenderStateContext mRenderStateContext;

  @Deprecated
  public static LayoutStateContext getTestInstance(ComponentContext c) {
    final LayoutState layoutState = new LayoutState(c);
    final LayoutStateContext layoutStateContext =
        new LayoutStateContext(
            layoutState, new TreeState(), c.getComponentTree(), null, null, INVALID_LAYOUT_VERSION);
    layoutState.setLayoutStateContextForTest(layoutStateContext);
    return layoutStateContext;
  }

  /**
   * This is only used in tests and marked as {@link Deprecated}. Use {@link
   * LayoutStateContext(LayoutState, ComponentTree, LayoutStateFuture, DiffNode, StateHandler)}
   * instead.
   *
   * @param layoutState
   * @param componentTree
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  @Deprecated
  public LayoutStateContext(
      final LayoutState layoutState, @Nullable final ComponentTree componentTree) {
    this(layoutState, new TreeState(), componentTree, null, null, INVALID_LAYOUT_VERSION);
  }

  @Deprecated
  LayoutStateContext(
      final LayoutState layoutState,
      final TreeState treeState,
      final @Nullable ComponentTree componentTree,
      final @Nullable LayoutStateFuture layoutStateFuture,
      final @Nullable DiffNode currentDiffTree,
      final int layoutVersion) {
    this(
        layoutState,
        layoutState.getIdGenerator(),
        layoutState.getComponentContext(),
        treeState,
        componentTree,
        layoutStateFuture,
        currentDiffTree,
        layoutVersion);
  }

  LayoutStateContext(
      final LayoutProcessInfo layoutProcessInfo,
      final RenderUnitIdGenerator idGenerator,
      final ComponentContext rootComponentContext,
      final TreeState treeState,
      final @Nullable ComponentTree componentTree,
      final @Nullable LayoutStateFuture layoutStateFuture,
      final @Nullable DiffNode currentDiffTree,
      final int layoutVersion) {
    mLayoutProcessInfo = layoutProcessInfo;
    mComponentTree = componentTree;
    mLayoutStateFuture = layoutStateFuture;
    mCurrentDiffTree = currentDiffTree;
    mTreeState = treeState;
    mRootComponentContext = rootComponentContext;
    mRenderStateContext = new RenderStateContext(mLayoutStateFuture, mTreeState, idGenerator, this);
    mCache = mRenderStateContext.getCache().getLayoutPhaseMeasuredResultCache();
    mLayoutVersion = layoutVersion;
    mThreadCreatedOn = Thread.currentThread().getName();
  }

  /**
   * Returns the RenderStateContext. This method is temporary and access to RSC will be moved to
   * different places depending on the place its needed from. This method will be removed soon and
   * LSC will no longer hold a reference to RSC, so do not add usages to this method.
   */
  @Deprecated
  public RenderStateContext getRenderStateContext() {
    return mRenderStateContext;
  }

  void releaseReference() {
    mLayoutProcessInfo = null;
    mTreeState = null;
    mLayoutStateFuture = null;
    mCurrentDiffTree = null;
    mRootComponentContext = null;
    mRenderStateContext.release();
    mPerfEvent = null;
    mThreadReleasedOn.add(Thread.currentThread().getName());
    mIsReleased = true;
  }

  /**
   * Returns true if this layout associated with this instance is completed and no longer in use.
   */
  public boolean isReleased() {
    return mIsReleased;
  }

  /** Returns the root component-context for the entire tree. */
  @Nullable
  ComponentContext getRootComponentContext() {
    return mRootComponentContext;
  }

  @Nullable
  LayoutProcessInfo getLayoutProcessInfo() {
    return mLayoutProcessInfo;
  }

  int getLayoutVersion() {
    return mLayoutVersion;
  }

  @Nullable
  @VisibleForTesting
  public ComponentTree getComponentTree() {
    return mComponentTree;
  }

  public @Nullable LayoutStateFuture getLayoutStateFuture() {
    return mLayoutStateFuture;
  }

  public @Nullable DiffNode getCurrentDiffTree() {
    return mCurrentDiffTree;
  }

  void setNestedTreeDiffNode(@Nullable DiffNode diff) {
    mCurrentNestedTreeDiffNode = diff;
  }

  boolean hasNestedTreeDiffNodeSet() {
    return mCurrentNestedTreeDiffNode != null;
  }

  public @Nullable DiffNode consumeNestedTreeDiffNode() {
    final DiffNode node = mCurrentNestedTreeDiffNode;
    mCurrentNestedTreeDiffNode = null;
    return node;
  }

  TreeState getTreeState() {
    return Preconditions.checkNotNull(mTreeState);
  }

  LayoutPhaseMeasuredResultCache getCache() {
    return mCache;
  }

  @Nullable
  public PerfEvent getPerfEvent() {
    return mPerfEvent;
  }

  public void setPerfEvent(@Nullable PerfEvent perfEvent) {
    mPerfEvent = perfEvent;
  }

  public void markLayoutResumed() {
    mThreadResumedOn.add(Thread.currentThread().getName());
  }

  public String getLifecycleDebugString() {
    StringBuilder builder = new StringBuilder();

    builder
        .append("LayoutStateContext was created on: ")
        .append(mThreadCreatedOn)
        .append("\n")
        .append("LayoutStateContext was released on: [");

    for (String thread : mThreadReleasedOn) {
      builder.append(thread).append(" ,");
    }

    builder.append("]").append("LayoutStateContext was resumed on: [");

    for (String thread : mThreadResumedOn) {
      builder.append(thread).append(" ,");
    }

    builder.append("]");

    return builder.toString();
  }
}
