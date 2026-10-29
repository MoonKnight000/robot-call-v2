package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

/** Closes the companies whose end date has passed. */
@Service
public class CompanyExpiryJob {

    private final CompanyUseCase companyUseCase;
    private final ClusterJobLock clusterJobLock;

    public CompanyExpiryJob(CompanyUseCase companyUseCase, ClusterJobLock clusterJobLock) {
        this.companyUseCase = companyUseCase;
        this.clusterJobLock = clusterJobLock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireLapsedCompanies() {
        clusterJobLock.runExclusively("company.expiry", companyUseCase::expireLapsedCompanies);
    }
}
