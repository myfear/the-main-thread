import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { colorBuildings, createBuildings, createDistrictOutlines, type BuildingInstances } from './geometry';
import type { Building, CityLayout, District } from './layout';
import { methodHeight, type ColorMetric, type SnapshotType } from './model';

interface Callbacks {
  select: (id: string) => void;
  hover: (id: string | null, x: number, y: number) => void;
  unavailable: () => void;
  rendered: () => void;
}

export class CityScene {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.OrthographicCamera(-20, 20, 20, -20, 0.1, 3000);
  private readonly controls: OrbitControls;
  private readonly ground: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshStandardMaterial>;
  private readonly sun = new THREE.DirectionalLight(0xffffff, 2.7);
  private readonly highlight = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1, 1, 1)),
    new THREE.LineBasicMaterial({ color: '#e61e73', depthTest: false }));
  private readonly labels = document.createElement('div');
  private readonly observer: ResizeObserver;
  private readonly raycaster = new THREE.Raycaster();
  private instances: BuildingInstances | null = null;
  private outlines: THREE.LineSegments | null = null;
  private layout: CityLayout | null = null;
  private types = new Map<string, SnapshotType>();
  private placements = new Map<string, Building>();
  private districtLabels: { element: HTMLDivElement; district: District }[] = [];
  private frame: number | null = null;
  private lost = false;
  private disposed = false;
  private baseSpan = 30;
  private baseAspect = 1;
  private pointerStart: { x: number; y: number } | null = null;

  constructor(private readonly host: HTMLElement, private readonly callbacks: Callbacks) {
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.scene.background = new THREE.Color('#f1f0eb');
    this.renderer.domElement.tabIndex = 0;
    this.renderer.domElement.setAttribute('aria-label', '3D software city');
    this.renderer.domElement.setAttribute('aria-describedby', 'city-instructions');
    this.host.append(this.renderer.domElement);
    this.labels.className = 'district-labels';
    this.labels.setAttribute('aria-hidden', 'true');
    this.host.append(this.labels);
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = false;
    this.controls.maxPolarAngle = Math.PI / 2 - 0.05;
    this.controls.minPolarAngle = 0.12;
    this.controls.minZoom = 0.2;
    this.controls.maxZoom = 20;
    this.controls.listenToKeyEvents(this.renderer.domElement);
    this.controls.addEventListener('change', this.requestRender);
    this.scene.add(new THREE.HemisphereLight(0xffffff, '#aaa3b9', 2), this.sun);
    this.sun.castShadow = true;
    this.sun.shadow.mapSize.set(2048, 2048);
    this.sun.shadow.bias = -0.0003;
    this.sun.shadow.normalBias = 0.1;
    this.ground = new THREE.Mesh(new THREE.PlaneGeometry(1, 1),
      new THREE.MeshStandardMaterial({ color: '#e6e4dd', roughness: 1 }));
    this.ground.rotation.x = -Math.PI / 2;
    this.ground.receiveShadow = true;
    this.scene.add(this.ground);
    this.highlight.visible = false;
    this.highlight.renderOrder = 10;
    this.scene.add(this.highlight);
    this.renderer.domElement.addEventListener('pointerdown', this.pointerDown);
    this.renderer.domElement.addEventListener('pointermove', this.pointerMove);
    this.renderer.domElement.addEventListener('pointerup', this.pointerUp);
    this.renderer.domElement.addEventListener('pointerleave', this.pointerLeave);
    this.renderer.domElement.addEventListener('webglcontextlost', this.contextLost);
    this.observer = new ResizeObserver(this.resize);
    this.observer.observe(host);
    this.resize();
  }

  load(types: readonly SnapshotType[], layout: CityLayout): void {
    if (this.instances) {
      this.scene.remove(this.instances.mesh);
      this.instances.mesh.geometry.dispose();
      (this.instances.mesh.material as THREE.Material).dispose();
      this.instances.mesh.dispose();
    }
    if (this.outlines) {
      this.scene.remove(this.outlines);
      this.outlines.geometry.dispose();
      (this.outlines.material as THREE.Material).dispose();
    }
    this.types = new Map(types.map(type => [type.id, type]));
    this.placements = new Map(layout.buildings.map(building => [building.id, building]));
    this.layout = layout;
    this.instances = createBuildings(layout, types);
    this.outlines = createDistrictOutlines(layout);
    this.scene.add(this.instances.mesh, this.outlines);
    const size = Math.max(20, layout.bounds.width);
    this.ground.scale.set(size + 12, size + 12, 1);
    this.sun.position.set(size * 0.4, size * 0.85, size * 0.6);
    Object.assign(this.sun.shadow.camera, {
      left: -size, right: size, top: size, bottom: -size, near: 0.1, far: size * 4,
    });
    this.sun.shadow.camera.updateProjectionMatrix();
    this.highlight.visible = false;
    this.labels.replaceChildren();
    this.districtLabels = layout.districts
      .filter(district => district.kind === 'module' || district.ownsTypes)
      .sort((a, b) => b.width * b.depth - a.width * a.depth || (a.id < b.id ? -1 : 1))
      .slice(0, 8).map(district => {
        const element = document.createElement('div');
        element.className = 'district-label';
        element.textContent = district.kind === 'module' ? district.module
          : district.packageName.split('.').slice(-2).join('.') || '(default package)';
        this.labels.append(element);
        return { element, district };
      });
    this.color('methodCount', null);
    this.reset();
  }

  color(metric: ColorMetric, matches: ReadonlySet<string> | null): number {
    const maximum = this.instances ? colorBuildings(this.instances, this.types, metric, matches) : 0;
    this.requestRender();
    return maximum;
  }

  select(id: string | null): void {
    const placement = id ? this.placements.get(id) : null;
    const type = id ? this.types.get(id) : null;
    this.highlight.visible = !!placement && !!type;
    if (placement && type) {
      const height = methodHeight(type.metrics.head.methodCount);
      this.highlight.position.set(placement.x, height / 2 + 0.04, placement.z);
      this.highlight.scale.set(placement.width + 0.12, height + 0.12, placement.depth + 0.12);
    }
    this.requestRender();
  }

  reset(): void {
    const size = Math.max(20, this.layout?.bounds.width ?? 20);
    let height = 1;
    for (const type of this.types.values()) height = Math.max(height, methodHeight(type.metrics.head.methodCount));
    this.frameBox(new THREE.Box3(new THREE.Vector3(-size / 2, 0, -size / 2),
      new THREE.Vector3(size / 2, height, size / 2)), true);
  }

  focus(id: string): void {
    const placement = this.placements.get(id);
    const type = this.types.get(id);
    if (!placement || !type) return;
    const height = methodHeight(type.metrics.head.methodCount);
    const padding = Math.max(1, Math.max(placement.width, placement.depth) * 0.35);
    this.frameBox(new THREE.Box3(
      new THREE.Vector3(placement.x - placement.width / 2 - padding, 0, placement.z - placement.depth / 2 - padding),
      new THREE.Vector3(placement.x + placement.width / 2 + padding, height, placement.z + placement.depth / 2 + padding)),
    false);
  }

  private frameBox(box: THREE.Box3, resetDirection: boolean): void {
    const center = box.getCenter(new THREE.Vector3());
    const direction = resetDirection ? new THREE.Vector3(1, 1.25, 1.1).normalize()
      : this.camera.position.clone().sub(this.controls.target).normalize();
    this.controls.target.copy(center);
    this.camera.position.copy(center).addScaledVector(direction, Math.max(50, box.getSize(new THREE.Vector3()).length() * 2));
    this.camera.zoom = 1;
    this.camera.lookAt(center);
    this.camera.updateMatrixWorld(true);
    let extentX = 0;
    let extentY = 0;
    for (const x of [box.min.x, box.max.x]) {
      for (const y of [box.min.y, box.max.y]) {
        for (const z of [box.min.z, box.max.z]) {
          const point = new THREE.Vector3(x, y, z).applyMatrix4(this.camera.matrixWorldInverse);
          extentX = Math.max(extentX, Math.abs(point.x));
          extentY = Math.max(extentY, Math.abs(point.y));
        }
      }
    }
    this.baseAspect = this.host.clientWidth / Math.max(1, this.host.clientHeight);
    this.baseSpan = Math.max(extentY * 2, extentX * 2 / this.baseAspect, 4) * 1.12;
    this.resize();
    this.controls.update();
    this.requestRender();
  }

  private resize = (): void => {
    const width = Math.max(1, this.host.clientWidth);
    const height = Math.max(1, this.host.clientHeight);
    this.renderer.setSize(width, height, false);
    const aspect = width / height;
    const span = this.baseSpan * Math.max(1, this.baseAspect / aspect);
    this.camera.left = -span * aspect / 2;
    this.camera.right = span * aspect / 2;
    this.camera.top = span / 2;
    this.camera.bottom = -span / 2;
    this.camera.updateProjectionMatrix();
    this.requestRender();
  };

  private requestRender = (): void => {
    if (this.frame !== null || this.lost || this.disposed) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = null;
      this.renderer.render(this.scene, this.camera);
      this.positionLabels();
      this.callbacks.rendered();
    });
  };

  private positionLabels(): void {
    const placed: { left: number; top: number; width: number; height: number }[] = [];
    for (const { element, district } of this.districtLabels) {
      const point = new THREE.Vector3(district.x, 0.06, district.z - district.depth / 2)
        .project(this.camera);
      const corner = new THREE.Vector3(district.x + district.width / 2, 0, district.z).project(this.camera);
      const left = (point.x + 1) * this.host.clientWidth / 2 - element.offsetWidth / 2;
      const top = (1 - point.y) * this.host.clientHeight / 2;
      const rect = { left, top, width: element.offsetWidth, height: element.offsetHeight };
      const visible = point.z > -1 && point.z < 1 && left > 8 && top > 8
        && left + rect.width < this.host.clientWidth - 8 && top < this.host.clientHeight - 50
        && Math.abs(corner.x - point.x) * this.host.clientWidth > rect.width
        && !placed.some(other => rect.left < other.left + other.width + 6
          && rect.left + rect.width + 6 > other.left && rect.top < other.top + other.height + 6
          && rect.top + rect.height + 6 > other.top);
      element.style.visibility = visible ? 'visible' : 'hidden';
      element.style.left = left + 'px';
      element.style.top = top + 'px';
      if (visible) placed.push(rect);
    }
  }

  private pick(x: number, y: number): string | null {
    if (!this.instances || this.lost) return null;
    const bounds = this.renderer.domElement.getBoundingClientRect();
    const point = new THREE.Vector2((x - bounds.left) / bounds.width * 2 - 1,
      -(y - bounds.top) / bounds.height * 2 + 1);
    this.camera.updateMatrixWorld(true);
    this.raycaster.setFromCamera(point, this.camera);
    const hit = this.raycaster.intersectObject(this.instances.mesh, false)[0];
    return hit?.instanceId === undefined ? null : this.instances.ids[hit.instanceId] ?? null;
  }
  private pointerDown = (event: PointerEvent): void => {
    this.pointerStart = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
  };
  private pointerMove = (event: PointerEvent): void => {
    this.callbacks.hover(event.buttons || event.pointerType === 'touch' ? null : this.pick(event.clientX, event.clientY),
      event.clientX, event.clientY);
  };
  private pointerUp = (event: PointerEvent): void => {
    if (this.pointerStart && Math.hypot(event.clientX - this.pointerStart.x, event.clientY - this.pointerStart.y) < 5) {
      const id = this.pick(event.clientX, event.clientY);
      if (id) this.callbacks.select(id);
    }
    this.pointerStart = null;
  };
  private pointerLeave = (): void => { this.callbacks.hover(null, 0, 0); };
  private contextLost = (event: Event): void => {
    event.preventDefault();
    this.lost = true;
    if (this.frame !== null) cancelAnimationFrame(this.frame);
    this.frame = null;
    this.labels.hidden = true;
    this.callbacks.unavailable();
  };

  dispose(): void {
    this.disposed = true;
    this.observer.disconnect();
    this.controls.dispose();
    if (this.frame !== null) cancelAnimationFrame(this.frame);
    const canvas = this.renderer.domElement;
    canvas.removeEventListener('pointerdown', this.pointerDown);
    canvas.removeEventListener('pointermove', this.pointerMove);
    canvas.removeEventListener('pointerup', this.pointerUp);
    canvas.removeEventListener('pointerleave', this.pointerLeave);
    canvas.removeEventListener('webglcontextlost', this.contextLost);
    this.scene.traverse(object => {
      if (object instanceof THREE.Mesh || object instanceof THREE.LineSegments) {
        object.geometry.dispose();
        const materials = Array.isArray(object.material) ? object.material : [object.material];
        materials.forEach(material => material.dispose());
      }
    });
    this.instances?.mesh.dispose();
    this.sun.shadow.dispose();
    this.renderer.dispose();
    canvas.remove();
    this.labels.remove();
  }
}
