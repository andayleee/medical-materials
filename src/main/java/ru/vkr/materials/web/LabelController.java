package ru.vkr.materials.web;

import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.vkr.materials.security.LaboratoryPrincipal;
import ru.vkr.materials.service.*;

@Controller
public class LabelController {
    private final LabelService labels;
    private final InventoryReadService read;
    public LabelController(LabelService labels,InventoryReadService read) { this.labels=labels;this.read=read; }
    @GetMapping("/inventory/batches/{id}/label")
    @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public String label(@PathVariable long id,Model model) { model.addAttribute("label",labels.label(id));return "inventory/label"; }
    @GetMapping(value="/inventory/batches/{id}/label.png",produces="image/png")
    @ResponseBody @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ResponseEntity<byte[]> preview(@PathVariable long id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(labels.preview(id)); }
    @GetMapping("/inventory/batches/{id}/label-job")
    @ResponseBody @PreAuthorize("hasAnyRole('ADMIN','STOREKEEPER')")
    public ResponseEntity<Map<String,String>> job(@PathVariable long id,@RequestParam(defaultValue="1") int copies,@RequestParam(defaultValue="2") double gap) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("data",labels.job(id,copies,gap)));
    }
    @GetMapping(value="/operations/{id}/barcode.svg",produces="image/svg+xml") @ResponseBody
    public ResponseEntity<String> documentBarcode(@PathVariable long id,@AuthenticationPrincipal LaboratoryPrincipal principal) {
        read.document(id,principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Code128.svg(Barcode.document(id)));
    }
}
