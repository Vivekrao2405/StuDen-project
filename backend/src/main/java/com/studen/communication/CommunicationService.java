package com.studen.communication;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.communication.audience.AudienceService;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campaign/template/segment CRUD. Audience resolution/counting is always delegated to {@link
 * AudienceService} — nothing here ever builds a query itself. Campaign mutation is deliberately
 * restricted to DRAFT (spec: a sent campaign's snapshot must never be rewritten by a later edit).
 */
@Service
public class CommunicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommunicationCampaignRepository campaignRepository;
    private final CommunicationTemplateRepository templateRepository;
    private final CommunicationSegmentRepository segmentRepository;
    private final UserRepository userRepository;
    private final AudienceService audienceService;

    public CommunicationService(CommunicationCampaignRepository campaignRepository,
            CommunicationTemplateRepository templateRepository, CommunicationSegmentRepository segmentRepository,
            UserRepository userRepository, AudienceService audienceService) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.segmentRepository = segmentRepository;
        this.userRepository = userRepository;
        this.audienceService = audienceService;
    }

    // --- Campaigns -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CampaignPageResponse<CampaignSummaryResponse> listCampaigns(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return CampaignPageResponse.of(
                campaignRepository.findAllByOrderByCreatedAtDesc(pageable).map(CampaignSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaign(UUID id) {
        return CampaignDetailResponse.from(findCampaignOrThrow(id));
    }

    @Transactional
    public CampaignDetailResponse createCampaign(UUID adminId, CampaignRequest request) {
        validateChannels(request);
        User admin = userRepository.getReferenceById(adminId);
        CommunicationCampaign campaign = new CommunicationCampaign(request.name(), request.category(),
                normalizedFilter(request.filterJson()), admin);
        applyRequest(campaign, request);
        return CampaignDetailResponse.from(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDetailResponse updateCampaign(UUID id, CampaignRequest request) {
        validateChannels(request);
        CommunicationCampaign campaign = findCampaignOrThrow(id);
        requireDraft(campaign);
        campaign.setName(request.name());
        campaign.setCategory(request.category());
        campaign.setFilterJson(normalizedFilter(request.filterJson()));
        applyRequest(campaign, request);
        return CampaignDetailResponse.from(campaignRepository.save(campaign));
    }

    @Transactional
    public void cancelCampaign(UUID id) {
        CommunicationCampaign campaign = findCampaignOrThrow(id);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new ConflictException("Only a draft or scheduled campaign can be cancelled");
        }
        campaign.setStatus(CampaignStatus.CANCELLED);
        campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public AudiencePreviewResponse previewAudience(String filterJson) {
        String normalized = normalizedFilter(filterJson);
        return new AudiencePreviewResponse(audienceService.count(normalized), audienceService.previewSample(normalized));
    }

    CommunicationCampaign findCampaignOrThrow(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
    }

    private void requireDraft(CommunicationCampaign campaign) {
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ConflictException("Only a draft campaign can be edited");
        }
    }

    private void validateChannels(CampaignRequest request) {
        if (!request.sendEmail() && !request.sendPush() && !request.sendInapp()) {
            throw new InvalidRequestException("Select at least one channel: Email, Push, or In-App");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("Campaign name is required");
        }
    }

    private void applyRequest(CommunicationCampaign campaign, CampaignRequest request) {
        campaign.setMarketing(request.marketing());
        if (request.templateId() != null) {
            campaign.setTemplate(templateRepository.findById(request.templateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template not found")));
        } else {
            campaign.setTemplate(null);
        }
        if (request.segmentId() != null) {
            campaign.setSegment(segmentRepository.findById(request.segmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Segment not found")));
        } else {
            campaign.setSegment(null);
        }
        campaign.setSendEmail(request.sendEmail());
        campaign.setSendPush(request.sendPush());
        campaign.setSendInapp(request.sendInapp());
        campaign.setEmailSubject(request.emailSubject());
        campaign.setEmailBodyHtml(request.emailBodyHtml());
        campaign.setPushTitle(request.pushTitle());
        campaign.setPushBody(request.pushBody());
        campaign.setInappTitle(request.inappTitle());
        campaign.setInappBody(request.inappBody());
        campaign.setCtaText(request.ctaText());
        campaign.setCtaUrl(request.ctaUrl());
    }

    private String normalizedFilter(String filterJson) {
        // AudienceFilterParser already defaults blank to "match everyone" — this just makes sure
        // every stored campaign/segment has a well-formed, parseable value, never null.
        return filterJson == null ? "" : filterJson;
    }

    // --- Templates -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates(boolean includeArchived) {
        List<CommunicationTemplate> templates = includeArchived ? templateRepository.findAllByOrderByCreatedAtDesc()
                : templateRepository.findAllByArchivedFalseOrderByCreatedAtDesc();
        return templates.stream().map(TemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(UUID id) {
        return TemplateResponse.from(findTemplateOrThrow(id));
    }

    @Transactional
    public TemplateResponse createTemplate(UUID adminId, TemplateRequest request) {
        CommunicationTemplate template = new CommunicationTemplate(request.name(), request.category(),
                userRepository.getReferenceById(adminId));
        applyTemplateRequest(template, request);
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse updateTemplate(UUID id, TemplateRequest request) {
        CommunicationTemplate template = findTemplateOrThrow(id);
        template.setName(request.name());
        template.setCategory(request.category());
        applyTemplateRequest(template, request);
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse duplicateTemplate(UUID id, UUID adminId) {
        CommunicationTemplate original = findTemplateOrThrow(id);
        CommunicationTemplate copy = new CommunicationTemplate(original.getName() + " (copy)", original.getCategory(),
                userRepository.getReferenceById(adminId));
        copy.setEmailSubject(original.getEmailSubject());
        copy.setEmailBodyHtml(original.getEmailBodyHtml());
        copy.setPushTitle(original.getPushTitle());
        copy.setPushBody(original.getPushBody());
        copy.setInappTitle(original.getInappTitle());
        copy.setInappBody(original.getInappBody());
        copy.setCtaText(original.getCtaText());
        copy.setCtaUrl(original.getCtaUrl());
        return TemplateResponse.from(templateRepository.save(copy));
    }

    @Transactional
    public void archiveTemplate(UUID id) {
        CommunicationTemplate template = findTemplateOrThrow(id);
        template.setArchived(true);
        templateRepository.save(template);
    }

    private void applyTemplateRequest(CommunicationTemplate template, TemplateRequest request) {
        template.setEmailSubject(request.emailSubject());
        template.setEmailBodyHtml(request.emailBodyHtml());
        template.setPushTitle(request.pushTitle());
        template.setPushBody(request.pushBody());
        template.setInappTitle(request.inappTitle());
        template.setInappBody(request.inappBody());
        template.setCtaText(request.ctaText());
        template.setCtaUrl(request.ctaUrl());
    }

    private CommunicationTemplate findTemplateOrThrow(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Template not found"));
    }

    // --- Segments --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SegmentResponse> listSegments() {
        return segmentRepository.findAllByOrderByCreatedAtDesc().stream().map(SegmentResponse::from).toList();
    }

    @Transactional
    public SegmentResponse createSegment(UUID adminId, SegmentRequest request) {
        CommunicationSegment segment = new CommunicationSegment(request.name(), request.description(),
                normalizedFilter(request.filterJson()), userRepository.getReferenceById(adminId));
        return SegmentResponse.from(segmentRepository.save(segment));
    }

    @Transactional
    public SegmentResponse updateSegment(UUID id, SegmentRequest request) {
        CommunicationSegment segment = findSegmentOrThrow(id);
        segment.setName(request.name());
        segment.setDescription(request.description());
        segment.setFilterJson(normalizedFilter(request.filterJson()));
        return SegmentResponse.from(segmentRepository.save(segment));
    }

    @Transactional
    public void deleteSegment(UUID id) {
        segmentRepository.delete(findSegmentOrThrow(id));
    }

    @Transactional(readOnly = true)
    public AudiencePreviewResponse previewSegment(UUID id) {
        return previewAudience(findSegmentOrThrow(id).getFilterJson());
    }

    private CommunicationSegment findSegmentOrThrow(UUID id) {
        return segmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Segment not found"));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
