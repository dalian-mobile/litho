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

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static com.facebook.litho.Component.hasCachedNode;
import static com.facebook.litho.Component.isLayoutSpec;
import static com.facebook.litho.Component.isLayoutSpecWithSizeSpec;
import static com.facebook.litho.Component.isMountSpec;
import static com.facebook.litho.Component.isMountable;
import static com.facebook.litho.Component.isNestedTree;
import static com.facebook.litho.Component.sMeasureFunction;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.rendercore.RenderCoreSystrace;
import com.facebook.rendercore.RenderState.LayoutContext;
import com.facebook.yoga.YogaConstants;
import com.facebook.yoga.YogaFlexDirection;
import com.facebook.yoga.YogaNode;
import java.util.List;

@Nullsafe(Nullsafe.Mode.LOCAL)
class Layout {

  private static final String EVENT_START_CREATE_LAYOUT = "start_create_layout";
  private static final String EVENT_END_CREATE_LAYOUT = "end_create_layout";
  private static final String EVENT_START_RECONCILE = "start_reconcile_layout";
  private static final String EVENT_END_RECONCILE = "end_reconcile_layout";

  static @Nullable ResolvedTree createResolvedTree(
      final RenderStateContext renderStateContext,
      final ComponentContext c,
      final Component component,
      final int widthSpec,
      final int heightSpec,
      final @Nullable LithoNode current,
      final @Nullable PerfEvent layoutStatePerfEvent) {

    final boolean isReconcilable =
        isReconcilable(
            c, component, Preconditions.checkNotNull(renderStateContext.getTreeState()), current);

    try {
      applyStateUpdateEarly(renderStateContext, c, component, current);
    } catch (Exception ex) {
      ComponentUtils.handleWithHierarchy(c, component, ex);
      return null;
    }

    if (layoutStatePerfEvent != null) {
      final String event = isReconcilable ? EVENT_START_RECONCILE : EVENT_START_CREATE_LAYOUT;
      layoutStatePerfEvent.markerPoint(event);
    }

    final @Nullable LithoNode node;
    if (!isReconcilable) {
      node =
          create(
              renderStateContext,
              c,
              widthSpec,
              heightSpec,
              component,
              !c.shouldAlwaysResolveNestedTreeInMeasure(),
              null);

      // This needs to finish layout on the UI thread.
      if (node != null && renderStateContext.isLayoutInterrupted()) {
        if (layoutStatePerfEvent != null) {
          layoutStatePerfEvent.markerPoint(EVENT_END_CREATE_LAYOUT);
        }

        return new ResolvedTree(node);
      } else {
        // Layout is complete, disable interruption from this point on.
        renderStateContext.markLayoutUninterruptible();
      }
    } else {
      final String globalKeyToReuse = Preconditions.checkNotNull(current).getHeadComponentKey();

      if (globalKeyToReuse == null) {
        throw new IllegalStateException("Cannot reuse a null global key");
      }

      final ComponentContext updatedScopedContext =
          update(renderStateContext, c, component, globalKeyToReuse);
      final Component updated = updatedScopedContext.getComponentScope();

      node =
          current.reconcile(
              renderStateContext,
              c,
              updated,
              updatedScopedContext.getScopedComponentInfo(),
              globalKeyToReuse);
    }

    if (layoutStatePerfEvent != null) {
      final String event = current == null ? EVENT_END_CREATE_LAYOUT : EVENT_END_RECONCILE;
      layoutStatePerfEvent.markerPoint(event);
    }

    return node == null ? null : new ResolvedTree(node);
  }

  static boolean isReconcilable(
      final ComponentContext c,
      final Component nextRootComponent,
      final TreeState treeState,
      final @Nullable LithoNode currentLayoutResult) {

    if (currentLayoutResult == null || !c.isReconciliationEnabled()) {
      return false;
    }

    if (!treeState.hasUncommittedUpdates()) {
      return false;
    }

    final Component currentRootComponent = currentLayoutResult.getHeadComponent();

    if (!nextRootComponent.getKey().equals(currentRootComponent.getKey())) {
      return false;
    }

    if (!ComponentUtils.isSameComponentType(currentRootComponent, nextRootComponent)) {
      return false;
    }

    return ComponentUtils.isEquivalent(currentRootComponent, nextRootComponent);
  }

  static @Nullable LithoLayoutResult measureTree(
      final LayoutStateContext layoutStateContext,
      final Context androidContext,
      final @Nullable LithoNode node,
      final int widthSpec,
      final int heightSpec,
      final @Nullable PerfEvent layoutStatePerfEvent) {
    if (node == null) {
      return null;
    }

    if (layoutStatePerfEvent != null) {
      layoutStatePerfEvent.markerPoint("start_measure");
    }

    final LithoLayoutResult result;

    final boolean isTracing = RenderCoreSystrace.isEnabled();
    if (isTracing) {
      RenderCoreSystrace.beginSection("measureTree:" + node.getHeadComponent().getSimpleName());
    }

    final LayoutContext<LithoRenderContext> context =
        new LayoutContext<>(
            androidContext, new LithoRenderContext(layoutStateContext), 0, null, null);

    result = node.calculateLayout(context, widthSpec, heightSpec);

    if (isTracing) {
      RenderCoreSystrace.endSection(/* measureTree */ );
    }

    if (layoutStatePerfEvent != null) {
      layoutStatePerfEvent.markerPoint("end_measure");
    }

    return result;
  }

  private static void applyStateUpdateEarly(
      final RenderStateContext renderStateContext,
      final ComponentContext c,
      final Component component,
      final @Nullable LithoNode current) {
    if (c.isApplyStateUpdateEarlyEnabled() && c.getComponentTree() != null) {
      renderStateContext.getTreeState().applyStateUpdatesEarly(c, component, current, false);
    }
  }

  public @Nullable static LithoNode create(
      final RenderStateContext renderStateContext,
      final ComponentContext parent,
      final Component component) {
    return create(renderStateContext, parent, component, null);
  }

  static @Nullable LithoNode create(
      final RenderStateContext renderStateContext,
      final ComponentContext parent,
      Component component,
      final @Nullable String globalKeyToReuse) {
    return create(
        renderStateContext,
        parent,
        SizeSpec.makeSizeSpec(0, SizeSpec.UNSPECIFIED),
        SizeSpec.makeSizeSpec(0, SizeSpec.UNSPECIFIED),
        component,
        false,
        globalKeyToReuse);
  }

  static @Nullable LithoNode create(
      final RenderStateContext renderStateContext,
      final ComponentContext parent,
      final int parentWidthSpec,
      final int parentHeightSpec,
      Component component,
      final boolean resolveNestedTree,
      final @Nullable String globalKeyToReuse) {

    final boolean isTracing = RenderCoreSystrace.isEnabled();
    if (isTracing) {
      RenderCoreSystrace.beginSection("createLayout:" + component.getSimpleName());
    }

    final LithoNode node;
    final ComponentContext c;
    final String globalKey;
    final boolean isNestedTree = isNestedTree(component);
    final boolean hasCachedNode = hasCachedNode(renderStateContext, component);
    final ScopedComponentInfo scopedComponentInfo;

    try {

      // 1. Consume the layout created in `willrender`.
      final LithoNode cached =
          component.consumeLayoutCreatedInWillRender(renderStateContext, parent);

      // 2. Return immediately if cached layout is available.
      if (cached != null) {
        return cached;
      }

      // 4. Update the component.
      // 5. Get the scoped context of the updated component.
      c = update(renderStateContext, parent, component, globalKeyToReuse);
      globalKey = c.getGlobalKey();

      component = c.getComponentScope();

      scopedComponentInfo = c.getScopedComponentInfo();
      // 6. Resolve the component into an InternalNode tree.

      final boolean shouldDeferNestedTreeResolution =
          (isNestedTree || hasCachedNode) && !resolveNestedTree;

      // If nested tree resolution is deferred, then create an nested tree holder.
      if (shouldDeferNestedTreeResolution) {
        node =
            new NestedTreeHolder(
                c.getTreeProps(), renderStateContext.getCache().getCachedNode(component));
      }

      // If the component can resolve itself resolve it.
      else if (component.canResolve()) {

        // Resolve the component into an InternalNode.
        node = component.resolve(renderStateContext, c);
      }

      // If the component is a MountSpec (including MountableComponents).
      else if (isMountSpec(component)) {

        // Create a blank InternalNode for MountSpecs and set the default flex direction.
        node = new LithoNode();
        node.flexDirection(YogaFlexDirection.COLUMN);

        // Call onPrepare for MountSpecs or prepare for MountableComponents.
        PrepareResult prepareResult = component.prepare(scopedComponentInfo.getContext());
        if (prepareResult != null) {
          node.setMountable(prepareResult.mountable);
        }
      }

      // If the component is a LayoutSpec.
      else if (isLayoutSpec(component)) {

        final RenderResult renderResult = component.render(c, parentWidthSpec, parentHeightSpec);
        final Component root = renderResult.component;

        if (root != null) {
          // TODO: (T57741374) this step is required because of a bug in redex.
          if (root == component) {
            node = root.resolve(renderStateContext, c);
          } else {
            node = create(renderStateContext, c, root);
          }
        } else {
          node = null;
        }

        if (renderResult != null && node != null) {
          applyRenderResultToNode(renderResult, node);
        }
      }

      // What even is this?
      else {
        throw new IllegalArgumentException("component:" + component.getSimpleName());
      }

      // 7. If the layout is null then return immediately.
      if (node == null) {
        return null;
      }

    } catch (Exception e) {
      ComponentUtils.handleWithHierarchy(parent, component, e);
      return null;
    } finally {
      if (isTracing) {
        RenderCoreSystrace.endSection();
      }
    }

    if (isTracing) {
      RenderCoreSystrace.beginSection("afterCreateLayout:" + component.getSimpleName());
    }

    // 8. Set the measure function
    // Set measure func on the root node of the generated tree so that the mount calls use
    // those (see Controller.mountNodeTree()). Handle the case where the component simply
    // delegates its layout creation to another component, i.e. the root node belongs to
    // another component.
    if (node.getComponentCount() == 0) {
      final boolean isMountSpecWithMeasure = component.canMeasure() && isMountSpec(component);
      if (isMountSpecWithMeasure || ((isNestedTree || hasCachedNode) && !resolveNestedTree)) {
        node.setMeasureFunction(sMeasureFunction);
      }
    }

    // 9. Copy the common props
    // Skip if resolving a layout with size spec because common props were copied in the previous
    // layout pass.
    final CommonProps commonProps = component.getCommonProps();
    if (commonProps != null && !(isLayoutSpecWithSizeSpec(component) && resolveNestedTree)) {
      commonProps.copyInto(c, node);
    }

    // 10. Add the component to the InternalNode.
    node.appendComponent(scopedComponentInfo);

    // 11. Create and add transition to this component's InternalNode.
    if (c.areTransitionsEnabled()) {
      if (component instanceof SpecGeneratedComponent
          && ((SpecGeneratedComponent) component).needsPreviousRenderData()) {
        node.addComponentNeedingPreviousRenderData(globalKey, scopedComponentInfo);
      } else {
        try {
          // Calls onCreateTransition on the Spec.
          final Transition transition = component.createTransition(c);
          if (transition != null) {
            node.addTransition(transition);
          }
        } catch (Exception e) {
          ComponentUtils.handleWithHierarchy(parent, component, e);
        }
      }
    }

    // 12. Add attachable components
    if (component instanceof SpecGeneratedComponent
        && ((SpecGeneratedComponent) component).hasAttachDetachCallback()) {
      // needs ComponentUtils.getGlobalKey?
      node.addAttachable(
          new LayoutSpecAttachable(
              globalKey, (SpecGeneratedComponent) component, scopedComponentInfo));
    }

    // 13. Add working ranges to the InternalNode.
    scopedComponentInfo.addWorkingRangeToNode(node);

    if (isTracing) {
      RenderCoreSystrace.endSection();
    }

    return node;
  }

  private static @Nullable LithoLayoutResult measureNestedTree(
      final LayoutStateContext layoutStateContext,
      ComponentContext parentContext,
      final NestedTreeHolderResult holderResult,
      final int widthSpec,
      final int heightSpec) {

    // 1. Check if current layout result is compatible with size spec and can be reused or not
    final @Nullable LithoLayoutResult currentLayout = holderResult.getNestedResult();
    final NestedTreeHolder node = holderResult.getNode();
    final Component component = node.getTailComponent();

    if (currentLayout != null
        && MeasureComparisonUtils.hasCompatibleSizeSpec(
            currentLayout.getLastWidthSpec(),
            currentLayout.getLastHeightSpec(),
            widthSpec,
            heightSpec,
            currentLayout.getLastMeasuredWidth(),
            currentLayout.getLastMeasuredHeight())) {
      return currentLayout;
    }

    // 2. Check if cached layout result is compatible and can be reused or not.
    final @Nullable LithoLayoutResult cachedLayout =
        consumeCachedLayout(layoutStateContext, node, holderResult, widthSpec, heightSpec);

    if (cachedLayout != null) {
      return cachedLayout;
    }

    // 3. If component is not using OnCreateLayoutWithSizeSpec, we don't have to resolve it again
    // and we can simply re-measure the tree. This is for cases where component was measured with
    // Component.measure API but we could not find the cached layout result or cached layout result
    // was not compatible with given size spec.
    if (currentLayout != null && !isLayoutSpecWithSizeSpec(component)) {
      return measureTree(
          layoutStateContext,
          currentLayout.getContext().getAndroidContext(),
          currentLayout.getNode(),
          widthSpec,
          heightSpec,
          null);
    }

    // 4. If current layout result is not available or component uses OnCreateLayoutWithSizeSpec
    // then resolve the tree and measure it. At this point we know that current layout result and
    // cached layout result are not available or are not compatible with given size spec.

    // NestedTree is used for two purposes i.e for components measured using Component.measure API
    // and for components which are OnCreateLayoutWithSizeSpec.
    // For components measured with measure API, we want to reuse the same global key calculated
    // during measure API call and for that we are using the cached node and accessing the global
    // key from it since NestedTreeHolder will have incorrect global key for it.
    final String globalKeyToReuse =
        isLayoutSpecWithSizeSpec(component)
            ? node.getTailComponentKey()
            : Preconditions.checkNotNull(node.getCachedNode()).getTailComponentKey();

    // 4.a Apply state updates early for layout phase
    if (parentContext.isApplyStateUpdateEarlyEnabled()) {
      layoutStateContext
          .getTreeState()
          .applyStateUpdatesEarly(parentContext, component, null, true);
    }

    // 4.b Create a new layout.
    final @Nullable LithoNode newNode =
        create(
            layoutStateContext.getRenderStateContext(),
            parentContext,
            widthSpec,
            heightSpec,
            component,
            true,
            Preconditions.checkNotNull(globalKeyToReuse));

    if (newNode == null) {
      return null;
    }

    holderResult.getNode().copyInto(newNode);

    // If the resolved tree inherits the layout direction, then set it now.
    if (newNode.isLayoutDirectionInherit()) {
      newNode.layoutDirection(holderResult.getResolvedLayoutDirection());
    }

    // Set the DiffNode for the nested tree's result to consume during measurement.
    layoutStateContext.setNestedTreeDiffNode(holderResult.getDiffNode());

    // 4.b Measure the tree
    return measureTree(
        layoutStateContext,
        parentContext.getAndroidContext(),
        newNode,
        widthSpec,
        heightSpec,
        null);
  }

  static @Nullable LithoLayoutResult measure(
      final LayoutStateContext layoutStateContext,
      ComponentContext parentContext,
      final NestedTreeHolderResult holder,
      final int widthSpec,
      final int heightSpec) {

    final LithoLayoutResult layout =
        measureNestedTree(layoutStateContext, parentContext, holder, widthSpec, heightSpec);

    final @Nullable LithoLayoutResult currentLayout = holder.getNestedResult();

    if (layout != null && layout != currentLayout) {
      // If layout created is not same as previous layout then set last width / heihgt, measdured
      // width and height specs
      layout.setLastWidthSpec(widthSpec);
      layout.setLastHeightSpec(heightSpec);
      layout.setLastMeasuredHeight(layout.getHeight());
      layout.setLastMeasuredWidth(layout.getWidth());

      // Set new created LayoutResult for future access
      holder.setNestedResult(layout);
    }

    return layout;
  }

  static void applyRenderResultToNode(RenderResult renderResult, LithoNode node) {
    if (renderResult.transitions != null) {
      for (Transition t : renderResult.transitions) {
        node.addTransition(t);
      }
    }
    if (renderResult.useEffectEntries != null) {
      for (Attachable attachable : renderResult.useEffectEntries) {
        node.addAttachable(attachable);
      }
    }
  }

  static ComponentContext update(
      final RenderStateContext renderStateContext,
      final ComponentContext parent,
      final Component component,
      @Nullable final String globalKeyToReuse) {

    final TreeProps ancestor = parent.getTreeProps();

    // 1. Update the internal state of the component wrt the parent.
    // 2. Get the scoped context from the updated component.
    final ComponentContext c =
        ComponentContext.withComponentScope(
            renderStateContext.getLayoutStateContext(),
            parent,
            component,
            globalKeyToReuse == null
                ? ComponentKeyUtils.generateGlobalKey(parent, parent.getComponentScope(), component)
                : globalKeyToReuse);
    c.getScopedComponentInfo().applyStateUpdates(renderStateContext.getTreeState());

    // 3. Set the TreeProps which will be passed to the descendants of the component.
    if (component instanceof SpecGeneratedComponent) {
      final TreeProps descendants =
          ((SpecGeneratedComponent) component).getTreePropsForChildren(c, ancestor);
      c.setParentTreeProps(ancestor);
      c.setTreeProps(descendants);
    }

    if (ComponentsConfiguration.isDebugModeEnabled) {
      DebugComponent.applyOverrides(c, component, c.getGlobalKey());
    }

    return c;
  }

  static ResolvedTree resumeResolvingTree(
      final RenderStateContext renderStateContext, final LithoNode root) {
    resume(renderStateContext, root);
    return new ResolvedTree(root);
  }

  private static void resume(final RenderStateContext c, final LithoNode root) {
    final List<Component> unresolved = root.getUnresolvedComponents();

    if (unresolved != null) {
      final ComponentContext context = root.getTailComponentContext();
      for (int i = 0, size = unresolved.size(); i < size; i++) {
        root.child(c, context, unresolved.get(i));
      }
      unresolved.clear();
    }

    for (int i = 0, size = root.getChildCount(); i < size; i++) {
      resume(c, root.getChildAt(i));
    }
  }

  @Nullable
  static LithoLayoutResult consumeCachedLayout(
      final LayoutStateContext layoutStateContext,
      final NestedTreeHolder holder,
      final NestedTreeHolderResult holderResult,
      final int widthSpec,
      final int heightSpec) {
    if (holder.getCachedNode() == null) {
      return null;
    }

    final LayoutPhaseMeasuredResultCache resultCache = layoutStateContext.getCache();
    final Component component = holder.getTailComponent();

    final @Nullable LithoLayoutResult cachedLayout =
        resultCache.getCachedResult(holder.getCachedNode());

    if (cachedLayout != null) {
      final boolean hasValidDirection =
          hasValidLayoutDirectionInNestedTree(holderResult, cachedLayout);
      final boolean hasCompatibleSizeSpec =
          MeasureComparisonUtils.hasCompatibleSizeSpec(
              cachedLayout.getLastWidthSpec(),
              cachedLayout.getLastHeightSpec(),
              widthSpec,
              heightSpec,
              cachedLayout.getLastMeasuredWidth(),
              cachedLayout.getLastMeasuredHeight());

      // Transfer the cached layout to the node it if it's compatible.
      if (hasValidDirection) {
        if (hasCompatibleSizeSpec) {
          return cachedLayout;
        } else if (!isLayoutSpecWithSizeSpec(component)) {
          return measureTree(
              layoutStateContext,
              cachedLayout.getContext().getAndroidContext(),
              cachedLayout.getNode(),
              widthSpec,
              heightSpec,
              null);
        }
      }
    }

    return null;
  }

  /**
   * Check that the root of the nested tree we are going to use, has valid layout directions with
   * its main tree holder node.
   */
  private static boolean hasValidLayoutDirectionInNestedTree(
      NestedTreeHolderResult holder, LithoLayoutResult nestedTree) {
    return nestedTree.getNode().isLayoutDirectionInherit()
        || (nestedTree.getResolvedLayoutDirection() == holder.getResolvedLayoutDirection());
  }

  static boolean shouldComponentUpdate(
      final LithoNode layoutNode, final @Nullable DiffNode diffNode) {
    if (diffNode == null) {
      return true;
    }

    final Component component = layoutNode.getTailComponent();
    final ComponentContext scopedContext = layoutNode.getTailComponentContext();

    // return true for mountables to exit early
    if (isMountable(component)) {
      return true;
    }

    try {
      return component.shouldComponentUpdate(
          getDiffNodeScopedContext(diffNode), diffNode.getComponent(), scopedContext, component);
    } catch (Exception e) {
      ComponentUtils.handleWithHierarchy(scopedContext, component, e);
    }

    return true;
  }

  /** DiffNode state should be retrieved from the committed LayoutState. */
  private static @Nullable ComponentContext getDiffNodeScopedContext(DiffNode diffNode) {
    final @Nullable ScopedComponentInfo scopedComponentInfo = diffNode.getScopedComponentInfo();

    if (scopedComponentInfo == null) {
      return null;
    }

    return scopedComponentInfo.getContext();
  }

  static boolean isLayoutDirectionRTL(final Context context) {
    ApplicationInfo applicationInfo = context.getApplicationInfo();

    if ((SDK_INT >= JELLY_BEAN_MR1)
        && (applicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0) {

      int layoutDirection = getLayoutDirection(context);
      return layoutDirection == View.LAYOUT_DIRECTION_RTL;
    }

    return false;
  }

  @TargetApi(JELLY_BEAN_MR1)
  private static int getLayoutDirection(final Context context) {
    return context.getResources().getConfiguration().getLayoutDirection();
  }

  static void setStyleWidthFromSpec(YogaNode node, int widthSpec) {
    switch (SizeSpec.getMode(widthSpec)) {
      case SizeSpec.UNSPECIFIED:
        node.setWidth(YogaConstants.UNDEFINED);
        break;
      case SizeSpec.AT_MOST:
        node.setMaxWidth(SizeSpec.getSize(widthSpec));
        break;
      case SizeSpec.EXACTLY:
        node.setWidth(SizeSpec.getSize(widthSpec));
        break;
    }
  }

  static void setStyleHeightFromSpec(YogaNode node, int heightSpec) {
    switch (SizeSpec.getMode(heightSpec)) {
      case SizeSpec.UNSPECIFIED:
        node.setHeight(YogaConstants.UNDEFINED);
        break;
      case SizeSpec.AT_MOST:
        node.setMaxHeight(SizeSpec.getSize(heightSpec));
        break;
      case SizeSpec.EXACTLY:
        node.setHeight(SizeSpec.getSize(heightSpec));
        break;
    }
  }
}
